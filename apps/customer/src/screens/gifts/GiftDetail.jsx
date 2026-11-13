// 선물 한 건 (코드에서 정함, 2026-11-13 친구·선물 5회차 — Figma 에 화면 없음).
// 들어오는 길: 선물함의 '보기', 선물 푸시(type gift + giftId), 선물 보내기 직후, 결제창에서 돌아온 뒤(GiftPayment.jsx).
// 받은 사람: 받기 전이면 '선물 받기'·'거절하기'. 보낸 사람: 받기 전이면 '선물 취소', 결제 전이면 '결제하기'(토스 결제창을 다시 연다).
// 결제 결과·상대의 결정을 기다리는 동안 5초마다, 푸시를 받으면 바로 다시 읽는다. 문구는 mock.js GIFTS_COPY.
import { useLocation, useNavigate, useParams } from 'react-router';
import {
  ScreenLayout, NavBar, Card, SummaryRow, Button, ScreenError, ScreenLoading, ActionError, useAction, usePolling, showToast,
} from '@ansimclean/ui';
import { getGift, acceptGift, rejectGift, cancelGift } from '../../api/gifts.js';
import { openPayment } from '../../api/payments.js';
import { onPushReceived } from '../../push/pushClient.js';
import { GIFTS_COPY as C } from '../../api/mock.js';
import { personLabel } from './giftLabels.js';
import styles from './gifts.module.css';

/** 선물 한 건 화면 (/gifts/:id) */
export default function GiftDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { data, error, reload, refresh, invalidate } = usePolling(() => getGift(id), [id], { interval: 5000, subscribe: onPushReceived });

  // 받기·거절·취소: 성공하면 최신 상태를 다시 읽고 알림 문구를 띄운다 (그 전에 나간 폴링 응답은 버린다)
  const act = useAction(async (fn, done) => {
    await fn(id);
    invalidate();
    await refresh().catch(() => {});
    showToast(done);
  });
  const pay = useAction(async () => {
    await openPayment(data.checkout);
  });

  const gift = data?.gift;
  const navBar = <NavBar title={C.detailTitle} onBack={() => navigate(`/gifts?tab=${gift?.direction ?? 'received'}`)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  const sent = gift.direction === 'sent';
  const checkout = data.checkout;
  const payable = sent && gift.status === 'awaiting_payment' && checkout?.status === 'pending' && checkout.payable;
  // 결제 전 선물인데 결제 주문이 승인 대기(authorized)면 '확인하고 있어요'
  const statusText = gift.status === 'awaiting_payment' && checkout && checkout.status !== 'pending'
    ? C.checking
    : C.statusLong[gift.status]?.[gift.direction];

  return (
    <ScreenLayout navBar={navBar} gap="md">
      <Card gap="sm">
        <p className={`text-caption-l ${styles.plain}`}>{sent ? C.summary.to : C.summary.from} · {personLabel(gift.person)}</p>
        <h1 className={`text-heading-s ${styles.plain}`}>{gift.label}</h1>
        <SummaryRow label={C.summary.amount} value={gift.amountLabel} emphasis="card" size="sm" />
        {gift.expiresLabel && <SummaryRow label={C.summary.expires} value={gift.expiresLabel} size="sm" />}
        <p className={`text-body-default ${styles.plain}`} role="status">{statusText}</p>
      </Card>
      {gift.message && (
        <Card tone="subtle" gap="xs">
          <p className={`text-caption-l ${styles.plain}`}>{C.summary.message}</p>
          <p className={`text-body-default ${styles.message}`}>{gift.message}</p>
        </Card>
      )}
      {location.state?.paymentCancelled && payable && (
        <p className="text-body-s" role="alert">{C.paymentCancelled}</p>
      )}
      <ActionError error={act.error} fallback={C.actionFailed} />
      <ActionError error={pay.error} fallback={C.paymentOpenFailed} />

      {!sent && gift.status === 'sent' && (
        <div className={styles.list}>
          <Button loading={act.loading} onClick={() => act.run(acceptGift, C.acceptedToast)}>{C.accept}</Button>
          <Button variant="outlineGray" chevron={false} disabled={act.loading} onClick={() => act.run(rejectGift, C.rejectedToast)}>{C.reject}</Button>
        </div>
      )}
      {!sent && gift.status === 'accepted' && (
        <Button variant="outline" chevron={false} onClick={() => navigate('/gifts?tab=vouchers')}>{C.toVouchers}</Button>
      )}
      {payable && (
        <Button loading={pay.loading} onClick={pay.run}>{C.pay}</Button>
      )}
      {sent && (gift.status === 'sent' || payable) && (
        <Button variant="outlineGray" chevron={false} loading={act.loading} onClick={() => act.run(cancelGift, C.cancelledToast)}>{C.cancelGift}</Button>
      )}
      {sent && ['rejected', 'expired', 'cancelled', 'payment_failed'].includes(gift.status) && gift.person?.userId && (
        <Button variant="outline" chevron={false} onClick={() => navigate(`/gifts/new?to=${encodeURIComponent(gift.person.userId)}`)}>{C.sendAgain}</Button>
      )}
    </ScreenLayout>
  );
}
