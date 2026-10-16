// 화면은 이 어댑터만 통해 결제 API를 부른다. 금액은 서버가 예약에서 계산한다.
import { http } from './http.js';

export const prepareCheckout = (bookingId, key) =>
  http.post(
    `/customer/bookings/${encodeURIComponent(bookingId)}/checkout`,
    {},
    { idempotencyKey: key }
  );
export const getPayment = (id) => http.get(`/customer/payments/${encodeURIComponent(id)}`);
export const confirmPayment = (id, paymentKey, amount) =>
  http.post(
    `/customer/payments/${encodeURIComponent(id)}/confirm`,
    { paymentKey, amount },
    { idempotencyKey: `confirm-${id}` }
  );

let sdkPromise;
/** 공식 SDK를 한 번 로드한다. 로드 실패하면 다음 시도에서 다시 받을 수 있다. */
export function loadPaymentSdk() {
  if (window.TossPayments) return Promise.resolve(window.TossPayments);
  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://js.tosspayments.com/v2/standard';
      script.async = true;
      const timeout = setTimeout(() => fail(), 15000);
      const fail = () => {
        clearTimeout(timeout);
        script.remove();
        sdkPromise = undefined;
        reject(new Error('결제창을 불러오지 못했어요. 다시 시도해주세요.'));
      };
      script.onerror = fail;
      script.onload = () => {
        clearTimeout(timeout);
        if (window.TossPayments) resolve(window.TossPayments);
        else fail();
      };
      document.head.appendChild(script);
    });
  }
  return sdkPromise;
}

export async function openPayment(checkout) {
  const TossPayments = await loadPaymentSdk();
  const returnUrl = new URL('/payments/checkout', window.location.origin);
  returnUrl.searchParams.set('booking', checkout.bookingId);
  return TossPayments(checkout.clientKey)
    .payment({ customerKey: 'ANONYMOUS' })
    .requestPayment({
      method: 'CARD',
      amount: { currency: checkout.currency, value: checkout.amount },
      orderId: checkout.orderId,
      orderName: checkout.orderName,
      successUrl: returnUrl.toString(),
      failUrl: returnUrl.toString(),
    });
}
