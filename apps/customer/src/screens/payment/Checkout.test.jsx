import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import Checkout from './Checkout.jsx';
import * as payments from '../../api/payments.js';
import * as gifts from '../../api/gifts.js';
vi.mock('../../api/payments.js', () => ({
  prepareCheckout: vi.fn(),
  getPayment: vi.fn(),
  confirmPayment: vi.fn(),
  openPayment: vi.fn(),
}));
// 금액권 (2026-11-13 친구·선물 5회차) — 기본은 쓰는 금액권도 가진 금액권도 없음
vi.mock('../../api/gifts.js', () => ({
  getBookingVoucher: vi.fn(),
  getVouchers: vi.fn(),
  applyVoucher: vi.fn(),
  removeVoucher: vi.fn(),
}));
const noVoucher = { bookingId: 'booking', voucherId: null, label: null, appliedAmount: 0, appliedLabel: '0원', remainingDue: 85000, remainingLabel: '85,000원', coveredByVoucher: false };
const usable = { id: 'v-1', label: '원룸 1회 청소', balance: 69000, balanceLabel: '69,000원', validUntilLabel: '2027년 11월 13일까지', usable: true };

const checkout = {
  id: 'order',
  bookingId: 'booking',
  orderId: 'order',
  amount: 85000,
  currency: 'KRW',
  status: 'pending',
  refundedAmount: 0,
  clientKey: 'test_ck_stub',
  payable: true,
};
const mount = (url = '/payments/checkout?booking=booking') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/payments/checkout" element={<Checkout />} />
      </Routes>
    </MemoryRouter>
  );
beforeEach(() => {
  vi.clearAllMocks();
  payments.prepareCheckout.mockResolvedValue(checkout);
  payments.getPayment.mockResolvedValue(checkout);
  gifts.getBookingVoucher.mockResolvedValue(noVoucher);
  gifts.getVouchers.mockResolvedValue({ vouchers: [] });
});
it('서버 금액으로 결제 버튼을 실행한다', async () => {
  mount();
  expect(await screen.findByText('85,000원')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '결제하기' }));
  expect(payments.openPayment).toHaveBeenCalledWith(checkout);
});
it('성공 리다이렉트만으로 완료를 표시하지 않고 서버 상태를 읽는다', async () => {
  payments.getPayment.mockResolvedValue({ ...checkout, status: 'authorized' });
  mount('/payments/checkout?booking=booking&orderId=order&paymentKey=key&amount=85000');
  expect(await screen.findByText(/결제 결과를 확인하고 있어요\. 창을/)).toBeInTheDocument();
  expect(payments.confirmPayment).toHaveBeenCalledWith('order', 'key', 85000);
  expect(screen.queryByText('결제가 완료되었어요.')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
});
it('실패 복귀는 승인 요청을 하지 않고 다시 결제할 수 있다', async () => {
  mount('/payments/checkout?booking=booking&orderId=order&code=PAY_PROCESS_CANCELED');
  expect(await screen.findByRole('button', { name: '결제하기' })).toBeInTheDocument();
  expect(payments.confirmPayment).not.toHaveBeenCalled();
});
it('이미 결제한 주문은 결제 버튼을 숨긴다', async () => {
  payments.getPayment.mockResolvedValue({ ...checkout, status: 'paid' });
  mount();
  expect(await screen.findByText('결제가 완료되었어요.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
});
it('취소된 예약은 결제 버튼을 숨긴다', async () => {
  payments.getPayment.mockResolvedValue({ ...checkout, payable: false });
  mount();
  expect(await screen.findByText('취소된 예약은 결제할 수 없어요.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
});
it('복귀 URL의 예약과 실제 결제 주문이 다르면 승인하지 않는다', async () => {
  payments.getPayment.mockResolvedValue({ ...checkout, bookingId: 'another' });
  mount('/payments/checkout?booking=booking&orderId=order&paymentKey=key&amount=85000');
  expect(
    await screen.findByText('결제 정보를 확인할 수 없어요. 내 예약에서 다시 열어주세요.')
  ).toBeInTheDocument();
  expect(payments.confirmPayment).not.toHaveBeenCalled();
});

it('금액권을 쓰면 새 키로 줄어든 금액을 다시 준비한다', async () => {
  gifts.getVouchers.mockResolvedValue({ vouchers: [usable, { ...usable, id: 'v-x', label: '선물 중', usable: false }] });
  mount();
  fireEvent.click(await screen.findByRole('button', { name: '금액권 쓰기' }));
  expect(screen.queryByRole('radio', { name: /선물 중/ })).not.toBeInTheDocument(); // 쓸 수 없는 금액권은 고르지 못한다
  const applied = { ...noVoucher, voucherId: 'v-1', label: '원룸 1회 청소', appliedAmount: 69000, appliedLabel: '69,000원', remainingDue: 16000, remainingLabel: '16,000원' };
  gifts.applyVoucher.mockResolvedValue(applied);
  gifts.getBookingVoucher.mockResolvedValue(applied);
  payments.prepareCheckout.mockResolvedValue({ ...checkout, id: 'order2', amount: 16000 });
  payments.getPayment.mockResolvedValue({ ...checkout, id: 'order2', amount: 16000 });
  const firstKey = payments.prepareCheckout.mock.calls[0][1];
  fireEvent.click(screen.getByRole('button', { name: '이 금액권 쓰기' }));
  expect(await screen.findByText('16,000원')).toBeInTheDocument();
  expect(gifts.applyVoucher).toHaveBeenCalledWith('booking', 'v-1');
  expect(screen.getByText('원룸 1회 청소 사용')).toBeInTheDocument();
  expect(payments.prepareCheckout.mock.calls.at(-1)[1]).not.toBe(firstKey);
  expect(screen.getByRole('button', { name: '금액권 빼기' })).toBeInTheDocument();
});
it('금액권이 전부 덮으면 결제를 준비하지 않는다', async () => {
  gifts.getBookingVoucher.mockResolvedValue({ ...noVoucher, voucherId: 'v-1', label: '후드 청소', appliedAmount: 85000, appliedLabel: '85,000원', remainingDue: 0, remainingLabel: '0원', coveredByVoucher: true });
  mount();
  expect(await screen.findByText('금액권으로 결제가 끝났어요.')).toBeInTheDocument();
  expect(payments.prepareCheckout).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: '금액권 빼기' })).toBeInTheDocument();
});
it('카드 승인 요청 뒤에는 금액권을 바꿀 수 없다', async () => {
  gifts.getVouchers.mockResolvedValue({ vouchers: [usable] });
  payments.getPayment.mockResolvedValue({ ...checkout, status: 'paid' });
  mount();
  expect(await screen.findByText('결제가 완료되었어요.')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '금액권 쓰기' })).not.toBeInTheDocument();
});
