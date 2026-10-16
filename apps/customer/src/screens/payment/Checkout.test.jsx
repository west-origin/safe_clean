import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import Checkout from './Checkout.jsx';
import * as payments from '../../api/payments.js';
vi.mock('../../api/payments.js', () => ({
  prepareCheckout: vi.fn(),
  getPayment: vi.fn(),
  confirmPayment: vi.fn(),
  openPayment: vi.fn(),
}));

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
