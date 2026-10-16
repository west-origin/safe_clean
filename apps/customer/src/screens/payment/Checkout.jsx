// (코드에서 정함) 예약 완료·내 예약의 결제 버튼 → 토스 결제창 → 이 화면으로 복귀.
// 결제 성공 URL은 인증 결과일 뿐이다. 서버에서 승인한 결과를 조회해 표시한다.
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
  usePolling,
} from '@ansimclean/ui';
import { prepareCheckout, getPayment, confirmPayment, openPayment } from '../../api/payments.js';
import { newIdempotencyKey } from '../../api/http.js';
import { PAYMENT_COPY } from '../../api/mock.js';

/** 일회/정기 모두 방문 회차 한 건의 결제. 자동결제·카드 저장은 하지 않는다. */
export default function Checkout() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const bookingId = params.get('booking');
  const [key] = useState(newIdempotencyKey);
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
        if (existing.bookingId !== bookingId) throw new Error(PAYMENT_COPY.invalid);
        await confirmPayment(callback.id, callback.key, amount);
        // idempotency 캐시의 '승인 중' 응답 대신 최신 DB 상태를 읽는다.
        const result = await getPayment(callback.id);
        if (result.bookingId !== bookingId) throw new Error(PAYMENT_COPY.invalid);
        window.history.replaceState(
          window.history.state,
          '',
          `/payments/checkout?booking=${encodeURIComponent(bookingId)}`
        );
        return result;
      }
      if (!bookingId) throw new Error(PAYMENT_COPY.invalid);
      const prepared = await prepareCheckout(bookingId, key);
      return getPayment(prepared.id);
    },
    [bookingId, key],
    { interval: 3000 }
  );
  const pay = useAction(async () => {
    await openPayment(data);
  });
  const navBar = <NavBar title={PAYMENT_COPY.title} onBack={() => navigate('/reservations')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const pending = data.status === 'pending' && data.payable;
  return (
    <ScreenLayout navBar={navBar}>
      <Card>
        <SummaryRow
          label={PAYMENT_COPY.amount}
          value={`${data.amount.toLocaleString('ko-KR')}원`}
        />
        <p className="text-body-default" role="status">
          {data.refundStatus === 'pending'
            ? PAYMENT_COPY.refunding
            : data.status === 'pending' && !data.payable
              ? PAYMENT_COPY.notPayable
              : (PAYMENT_COPY.status[data.status] ?? PAYMENT_COPY.checking)}
        </p>
        <p className="text-body-s">{PAYMENT_COPY.perVisit}</p>
        {data.refundedAmount > 0 && (
          <SummaryRow
            label={PAYMENT_COPY.refund}
            value={`${data.refundedAmount.toLocaleString('ko-KR')}원`}
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
      {data.status === 'authorized' && (
        <Button variant="secondary" onClick={reload}>
          {PAYMENT_COPY.refresh}
        </Button>
      )}
      {data.status === 'failed' && (
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
