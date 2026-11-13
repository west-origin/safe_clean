// 선물 결제창에서 돌아오는 곳 (코드에서 정함, 2026-11-13 친구·선물 5회차). 토스 결제창의 성공·실패 URL 이 /gifts/:id/payment?orderId=…&paymentKey=…&amount=… 다.
// 결제 성공 URL 은 인증 결과일 뿐이라 서버에 승인을 요청(기존 POST /customer/payments/{id}/confirm)한 뒤 선물 한 건 화면으로 바꿔 간다.
// 결제가 확정되면 서버가 같은 트랜잭션에서 선물을 보낸다 — 이 화면은 선물 상태를 직접 바꾸지 않는다. 실패·취소로 돌아오면 승인 요청 없이 선물 화면으로.
import { useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { NavBar, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getPayment, confirmPayment } from '../../api/payments.js';
import { GIFTS_COPY as C, PAYMENT_COPY } from '../../api/mock.js';

/** 선물 결제 승인 요청 화면 (/gifts/:id/payment) */
export default function GiftPayment() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  // 한 번만 읽어 둔다 — 선물 화면으로 바꿔 갈 때 PG 키가 주소(방문 기록)에 남지 않는다
  const [callback] = useState(() => ({
    orderId: params.get('orderId'),
    key: params.get('paymentKey'),
    amount: params.get('amount'),
    failed: params.has('code'),
  }));
  const { data, error, reload } = useAsync(async () => {
    if (callback.failed || !callback.orderId || !callback.key) return 'cancelled';
    const amount = Number(callback.amount);
    if (!Number.isSafeInteger(amount) || amount <= 0) throw new Error(PAYMENT_COPY.invalid);
    const existing = await getPayment(callback.orderId);
    if (existing.giftId !== id) throw new Error(PAYMENT_COPY.invalid); // 이 선물의 결제가 아니면 승인하지 않는다
    await confirmPayment(callback.orderId, callback.key, amount);
    return 'confirmed';
  }, [id]);
  useEffect(() => {
    if (data) navigate(`/gifts/${encodeURIComponent(id)}`, { replace: true, state: data === 'cancelled' ? { paymentCancelled: true } : null });
  }, [data, id, navigate]);

  const navBar = <NavBar title={C.detailTitle} onBack={() => navigate(`/gifts/${encodeURIComponent(id)}`, { replace: true })} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  return <ScreenLoading navBar={navBar} />;
}
