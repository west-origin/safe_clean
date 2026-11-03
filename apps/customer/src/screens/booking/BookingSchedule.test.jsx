// 날짜/시간 화면(BookingSchedule) 테스트 (2026-11-03 펼침 상자 시안): 처음엔 '방문 일' 상자가 펼쳐져 달력이 보이고,
// 날짜를 고르면 '방문 시간' 상자가 열린다. 시작 → 종료를 고르면 하단 '총 결제금액' 이 그 시간만큼 바뀌고 "다음" 이 켜진다.
// 서버는 가짜(test/fakeHttp.js)다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingSchedule from './BookingSchedule.jsx';
import { SCHEDULE_PRICE } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const won = (n) => `${n.toLocaleString('ko-KR')}원`;

describe('Booking/Schedule 날짜/시간', () => {
  beforeEach(() => sessionStorage.clear());

  it('날짜 → 시작·종료 시간을 고르면 금액이 바뀌고 다음이 켜진다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ frequency: 'oneTime' }));
    render(
      <MemoryRouter initialEntries={['/booking/home/schedule']}>
        <Routes>
          <Route path="/booking/:category/schedule" element={<BookingSchedule />} />
          <Route path="/booking/:category/options" element={<p>옵션 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const dateBox = await screen.findByRole('button', { name: '방문 일' });
    expect(dateBox).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('총 결제금액')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '자세히' })).toBeNull();
    expect(screen.getByText(won(SCHEDULE_PRICE.standardHours * SCHEDULE_PRICE.hourly))).toBeInTheDocument(); // 고르기 전: 표준 시간 금액
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();

    // 고를 수 있는 날 중 내일 이후 첫 날 (오늘은 남은 시간이 부족할 수 있다)
    const days = screen.getAllByRole('button', { name: /^\d+월 \d+일$/ }).filter((b) => !b.disabled);
    fireEvent.click(days[1]);
    expect(screen.getByRole('button', { name: '방문 시간' })).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(screen.getByRole('button', { name: '09:00' }));
    expect(screen.getByRole('button', { name: '10:00' })).toBeDisabled(); // 최소 2시간
    fireEvent.click(screen.getByRole('button', { name: '12:00' }));
    expect(screen.getByRole('button', { name: '09:00 ~ 12:00 (3시간)' })).toBeInTheDocument();
    expect(screen.getByText(won(3 * SCHEDULE_PRICE.hourly))).toBeInTheDocument();

    const nextBtn = screen.getByRole('button', { name: '다음' });
    expect(nextBtn).toBeEnabled();
    fireEvent.click(nextBtn);
    expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.home'));
    expect(draft).toMatchObject({ time: '09:00', endTime: '12:00', durationHours: 3 });
  });
});
