// 날짜/시간 화면(BookingSchedule) 테스트: 기본 2시간·표시 금액, 시간 시트의 +(1시간씩, 최대 8)와 '확인' 반영,
// 날짜·시간을 고르기 전엔 "다음" 이 꺼져 있는지 본다. 서버는 가짜(test/fakeHttp.js)다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingSchedule from './BookingSchedule.jsx';
import { SCHEDULE_PRICE } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const won = (n) => `${n.toLocaleString('ko-KR')}원`;

describe('Booking/Schedule 날짜/시간', () => {
  beforeEach(() => sessionStorage.clear());

  it('기본 2시간, 시트에서 1시간씩 늘려 최대 8시간까지, 확인을 눌러야 반영된다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ frequency: 'oneTime' }));
    render(
      <MemoryRouter initialEntries={['/booking/home/schedule']}>
        <Routes>
          <Route path="/booking/:category/schedule" element={<BookingSchedule />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('2시간')).toBeInTheDocument();
    expect(screen.getByText(won(2 * SCHEDULE_PRICE.hourly))).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled(); // 날짜·시간 전

    fireEvent.click(screen.getByRole('button', { name: '서비스 시간 변경' }));
    const plus = screen.getByRole('button', { name: '시간 늘리기' });
    for (let i = 0; i < 10; i += 1) if (!plus.disabled) fireEvent.click(plus);
    expect(screen.getByText('8시간 00분')).toBeInTheDocument();
    expect(plus).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByText('8시간')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home')).durationHours).toBe(8);
  });
});
