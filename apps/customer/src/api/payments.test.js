import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { prepareCheckout, confirmPayment, openPayment, loadPaymentSdk } from './payments.js';
import { http } from './http.js';
vi.mock('./http.js', () => ({ http: { post: vi.fn(), get: vi.fn() } }));

beforeEach(() => vi.clearAllMocks());
afterEach(() => {
  delete window.TossPayments;
});
it('결제 준비에 금액을 보내지 않고 멱등 키를 유지한다', async () => {
  await prepareCheckout('booking', 'same-key');
  expect(http.post).toHaveBeenCalledWith(
    '/customer/bookings/booking/checkout',
    {},
    { idempotencyKey: 'same-key' }
  );
  await confirmPayment('order', 'pg-key', 85000);
  expect(http.post).toHaveBeenLastCalledWith(
    '/customer/payments/order/confirm',
    { paymentKey: 'pg-key', amount: 85000 },
    { idempotencyKey: 'confirm-order' }
  );
});
it('서버 주문번호·금액으로 공식 결제창을 열고 현재 origin으로 돌아온다', async () => {
  const requestPayment = vi.fn().mockResolvedValue(undefined);
  window.TossPayments = vi.fn(() => ({ payment: () => ({ requestPayment }) }));
  await openPayment({
    bookingId: 'booking',
    orderId: 'server-order',
    amount: 85000,
    currency: 'KRW',
    clientKey: 'test_ck_stub',
    orderName: '방문 서비스',
  });
  expect(window.TossPayments).toHaveBeenCalledWith('test_ck_stub');
  expect(requestPayment).toHaveBeenCalledWith(
    expect.objectContaining({
      orderId: 'server-order',
      amount: { currency: 'KRW', value: 85000 },
      method: 'CARD',
    })
  );
  const params = requestPayment.mock.calls[0][0];
  expect(new URL(params.successUrl).origin).toBe(window.location.origin);
  expect(new URL(params.successUrl).searchParams.get('booking')).toBe('booking');
});
it('SDK 중복 다운로드를 막고 실패하면 재시도할 수 있다', async () => {
  const first = loadPaymentSdk();
  expect(loadPaymentSdk()).toBe(first);
  const script = document.querySelector('script[src="https://js.tosspayments.com/v2/standard"]');
  script.dispatchEvent(new Event('error'));
  await expect(first).rejects.toThrow('결제창');
  const retry = loadPaymentSdk();
  window.TossPayments = vi.fn();
  document
    .querySelector('script[src="https://js.tosspayments.com/v2/standard"]')
    .dispatchEvent(new Event('load'));
  await expect(retry).resolves.toBe(window.TossPayments);
});
