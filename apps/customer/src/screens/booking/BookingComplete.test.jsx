// 예약 완료 화면(BookingComplete) 테스트 (2026-11-03 시안): 제목·사용자 문구·금액 두 줄이 보이고,
// '홈으로 가기' → 홈, '예약상세 보기' → 내 예약. 예약은 가짜 서버에서 하나 만들어 id 로 연다.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingComplete from './BookingComplete.jsx';
import { createBooking } from '../../api/customer.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const inTwoDays = () => {
  const d = new Date();
  d.setDate(d.getDate() + 2);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const renderAt = (id) =>
  render(
    <MemoryRouter initialEntries={[`/booking/home/complete?id=${id}`]}>
      <Routes>
        <Route path="/booking/:category/complete" element={<BookingComplete />} />
        <Route path="/home" element={<p>홈 화면</p>} />
        <Route path="/reservations" element={<p>내 예약 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );

describe('Booking/Complete', () => {
  let booking;
  beforeEach(async () => {
    booking = await createBooking('home', { frequency: 'oneTime', date: inTwoDays(), time: '10:00', roadAddress: '서울 강남구 강남대로 396' });
  });

  it('제목·문구·금액, 예약상세 보기 → 내 예약', async () => {
    renderAt(booking.id);
    expect(await screen.findByRole('heading', { name: '예약 완료' })).toBeInTheDocument();
    expect(screen.getByText(/이제, 안심하고 맡기세요\./)).toBeInTheDocument();
    expect(screen.getByText('주문 금액')).toBeInTheDocument();
    expect(screen.getByText('총 결제 금액')).toBeInTheDocument();
    expect(screen.getAllByText(booking.totalLabel)).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '예약상세 보기' }));
    expect(await screen.findByText('내 예약 화면')).toBeInTheDocument();
  });

  // 가정 정기청소 첫 결제 혜택 (V075): 분해청소가 전액 할인되면 주문 금액은 정가, 총 결제 금액은 0원
  it('첫 결제 혜택으로 무료인 분해청소는 총 결제 금액이 0원', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.firstPaymentBenefit = true;
    const hood = await createBooking('appliance', { hoodSize: '900', date: inTwoDays(), time: '10:00', roadAddress: '서울 강남구 강남대로 396', submitKey: 'hood' });
    renderAt(hood.id);
    expect(await screen.findByText('150,000원')).toBeInTheDocument();
    expect(screen.getByText('0원')).toBeInTheDocument();
    expect(__fake.firstPaymentBenefit).toBe(false); // 한 번만
  });

  it('홈으로 가기 → 홈', async () => {
    renderAt(booking.id);
    fireEvent.click(await screen.findByRole('button', { name: '홈으로 가기' }));
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  });
});
