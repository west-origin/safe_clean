// (코드에서 정함) 예약 완료·내 예약의 결제 버튼 → 토스 결제창 → 이 화면으로 복귀.
// 결제 성공 URL은 인증 결과일 뿐이다. 서버에서 승인한 결과를 조회해 표시한다.
// 금액권(2026-11-13 친구·선물 5회차): 카드 승인 요청 전에 금액권을 쓰거나 뺄 수 있다(CheckoutVoucher). 바꾸면 서버가 결제 대기 주문을 닫으므로
// 새 멱등 키로 결제를 다시 준비한다(줄어든 금액). 금액권이 전부 덮으면 결제 준비를 부르지 않고 '금액권으로 결제가 끝났어요'.
// 요금표 전환(2026-11-13, 6-b): 주 N회 요금제 회차면 서버가 그 시리즈의 한 달치 주문을 준다(bookingId: null, seriesId). 어느 회차에서 열어도 같은 주문이다.
//   결제창에서 돌아올 때 ?series= 로 시리즈를 확인한다. 금액권은 한 달 결제에 한 장 — 서버가 회차 금액 비율로 나눠 쓴다(2026-11-14).
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  ScreenLayout,
  NavBar,
  Card,
  SummaryRow,
  Button,
  ScreenError,
  ScreenLoading,
  ActionError,
  useAction,
  useAsync,
  usePolling,
} from '@ansimclean/ui';
import { prepareCheckout, getPayment, confirmPayment, openPayment } from '../../api/payments.js';
import { getBookingVoucher, getVouchers } from '../../api/gifts.js';
import { newIdempotencyKey } from '../../api/http.js';
import { PAYMENT_COPY, GIFTS_COPY } from '../../api/mock.js';
import CheckoutVoucher from './CheckoutVoucher.jsx';

/** 방문 회차 한 건의 결제(주 N회 요금제면 그 시리즈 한 달치). 자동결제·카드 저장은 하지 않는다. */
export default function Checkout() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const bookingId = params.get('booking');
  const seriesId = params.get('series'); // 시리즈 결제창에서 돌아왔을 때만 (openPayment 가 붙인다)
  // 돌아온 주문이 이 예약(또는 이 예약의 시리즈) 것인지 — 서버도 소유자를 검사하지만, 다른 예약 화면에 다른 주문이 보이지 않게
  const ours = (p) => (p.seriesId ? Boolean(seriesId) && p.seriesId === seriesId : p.bookingId === bookingId);
  const [key, setKey] = useState(newIdempotencyKey); // 금액권을 바꾸면 새 키 → 새 금액으로 다시 준비
  // 쓸 수 있는 내 금액권 (못 받아도 결제는 된다 — 금액권 칸만 안 보인다)
  const mine = useAsync(() => getVouchers().then((r) => r.vouchers.filter((v) => v.usable)).catch(() => []), []);
  // 한 번만 읽어 보관하고 URL에서 PG 키를 제거한다(브라우저 방문 기록·공유에 남기지 않는다).
  const [callback] = useState(() => ({
    id: params.get('orderId'),
    key: params.get('paymentKey'),
    amount: params.get('amount'),
    failed: params.has('code'),
  }));
  const { data, error, reload } = usePolling(
    async () => {
      if (callback.id && callback.key) {
        const amount = Number(callback.amount);
        if (!Number.isSafeInteger(amount) || amount <= 0) throw new Error(PAYMENT_COPY.invalid);
        const existing = await getPayment(callback.id);
        if (!ours(existing)) throw new Error(PAYMENT_COPY.invalid);
        await confirmPayment(callback.id, callback.key, amount);
        // idempotency 캐시의 '승인 중' 응답 대신 최신 DB 상태를 읽는다.
        const result = await getPayment(callback.id);
        if (!ours(result)) throw new Error(PAYMENT_COPY.invalid);
        window.history.replaceState(
          window.history.state,
          '',
          `/payments/checkout?booking=${encodeURIComponent(bookingId)}${seriesId ? `&series=${encodeURIComponent(seriesId)}` : ''}`
        );
        return { payment: result, voucher: await getBookingVoucher(bookingId).catch(() => null) };
      }
      if (!bookingId) throw new Error(PAYMENT_COPY.invalid);
      const voucher = await getBookingVoucher(bookingId);
      if (voucher.coveredByVoucher) return { payment: null, voucher }; // 카드로 낼 금액이 없다
      const prepared = await prepareCheckout(bookingId, key);
      return { payment: await getPayment(prepared.id), voucher };
    },
    [bookingId, key],
    { interval: 3000 }
  );
  const pay = useAction(async () => {
    await openPayment(data.payment, bookingId);
  });
  const navBar = <NavBar title={PAYMENT_COPY.title} onBack={() => navigate('/reservations')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const { payment, voucher } = data;
  const series = Boolean(payment?.seriesId); // 주 N회 요금제 한 달치 결제
  const voucherSection = (editable) => (
    <CheckoutVoucher
      bookingId={bookingId}
      voucher={voucher}
      vouchers={mine.data ?? []}
      editable={editable && !callback.key}
      series={series}
      onChanged={() => {
        setKey(newIdempotencyKey());
        mine.reload();
      }}
    />
  );
  if (!payment) {
    // 금액권이 전부 덮었다 — 빼면 다시 카드 결제
    return (
      <ScreenLayout navBar={navBar}>
        {voucherSection(true)}
        <p className="text-body-default" role="status">{GIFTS_COPY.voucherCovered}</p>
        <Button variant="secondary" onClick={() => navigate('/reservations')}>
          {PAYMENT_COPY.reservations}
        </Button>
      </ScreenLayout>
    );
  }
  const pending = payment.status === 'pending' && payment.payable;
  return (
    <ScreenLayout navBar={navBar}>
      {voucherSection(pending)}
      <Card>
        <SummaryRow
          label={series ? PAYMENT_COPY.seriesAmount : PAYMENT_COPY.amount}
          value={`${payment.amount.toLocaleString('ko-KR')}원`}
        />
        <p className="text-body-default" role="status">
          {payment.refundStatus === 'pending'
            ? PAYMENT_COPY.refunding
            : payment.status === 'pending' && !payment.payable
              ? PAYMENT_COPY.notPayable
              : (PAYMENT_COPY.status[payment.status] ?? PAYMENT_COPY.checking)}
        </p>
        <p className="text-body-s">{series ? PAYMENT_COPY.series : PAYMENT_COPY.perVisit}</p>
        {payment.refundedAmount > 0 && (
          <SummaryRow
            label={PAYMENT_COPY.refund}
            value={`${payment.refundedAmount.toLocaleString('ko-KR')}원`}
          />
        )}
      </Card>
      {callback.failed && pending && (
        <p className="text-body-s" role="alert">
          {PAYMENT_COPY.cancelled}
        </p>
      )}
      {pending && (
        <Button loading={pay.loading} onClick={pay.run}>
          {PAYMENT_COPY.pay}
        </Button>
      )}
      {payment.status === 'authorized' && (
        <Button variant="secondary" onClick={reload}>
          {PAYMENT_COPY.refresh}
        </Button>
      )}
      {payment.status === 'failed' && (
        <Button
          onClick={() =>
            window.location.assign(`/payments/checkout?booking=${encodeURIComponent(bookingId)}`)
          }
        >
          {PAYMENT_COPY.retry}
        </Button>
      )}
      <ActionError error={pay.error} fallback={PAYMENT_COPY.failed} />
      <Button variant="secondary" onClick={() => navigate('/reservations')}>
        {PAYMENT_COPY.reservations}
      </Button>
    </ScreenLayout>
  );
}
