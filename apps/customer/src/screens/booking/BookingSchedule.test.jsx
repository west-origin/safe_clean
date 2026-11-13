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

  // 매장 청소는 요금표 밖 — 시간 요금 그대로 (요금표 전환 2026-11-13)
  it('날짜 → 시작·종료 시간을 고르면 금액이 바뀌고 다음이 켜진다', async () => {
    sessionStorage.setItem('ansimclean.booking.store', JSON.stringify({ frequency: 'oneTime' }));
    render(
      <MemoryRouter initialEntries={['/booking/store/schedule']}>
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
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.store'));
    expect(draft).toMatchObject({ time: '09:00', endTime: '12:00', durationHours: 3 });
  });

  const mount = (category) => render(
    <MemoryRouter initialEntries={[`/booking/${category}/schedule`]}>
      <Routes>
        <Route path="/booking/:category/schedule" element={<BookingSchedule />} />
        <Route path="/booking/:category/options" element={<p>옵션 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
  // 요금표 전환(2026-11-13): 가정 주 2회 — 요일 2개를 먼저 고르면 그 요일만 달력이 열리고, 시작만 고르면 3시간 뒤가 종료
  it('가정 주 N회는 요일 N개를 고르고, 시작 시간만 고르면 요금제 시간만큼 종료가 정해진다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ frequency: 'recurring', visitsPerWeek: 2 }));
    mount('home');
    expect(await screen.findByRole('heading', { name: '방문 요일 (주 2회)' })).toBeInTheDocument();
    expect(screen.getByText('599,000원')).toBeInTheDocument(); // 가정 주 2회 한 달
    expect(screen.getByText('방문 요일을 먼저 선택해 주세요.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '월' }));
    fireEvent.click(screen.getByRole('button', { name: '수' }));
    fireEvent.click(screen.getByRole('button', { name: '금' })); // 3개째 → 가장 먼저 고른 월이 빠진다
    expect(screen.getByRole('button', { name: '월' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: '수' })).toHaveAttribute('aria-pressed', 'true');
    // 달력: 고를 수 있는 날은 모두 수·금
    const open = screen.getAllByRole('button', { name: /^\d+월 \d+일$/ }).filter((b) => !b.disabled);
    expect(open.length).toBeGreaterThan(0);
    const now = new Date();
    for (const b of open) {
      const [, m, d] = b.getAttribute('aria-label').match(/^(\d+)월 (\d+)일$/);
      const day = new Date(now.getFullYear() + (Number(m) < now.getMonth() + 1 ? 1 : 0), Number(m) - 1, Number(d)).getDay();
      expect([3, 5]).toContain(day);
    }
    fireEvent.click(open[1]);
    fireEvent.click(screen.getByRole('button', { name: '10:00' }));
    expect(screen.getByRole('button', { name: '10:00 ~ 13:00 (3시간)' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '16:00' })).toBeDisabled(); // 16 + 3 = 19시 — 시간 목록(18시까지) 밖
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home'))).toMatchObject({ weekdays: [2, 4], time: '10:00', endTime: '13:00', durationHours: 3 });
  });

  it('분해청소는 요일 없이 후드 크기 요금, 2시간 고정', async () => {
    sessionStorage.setItem('ansimclean.booking.appliance', JSON.stringify({ frequency: 'oneTime', hoodSize: '1200' }));
    mount('appliance');
    expect(await screen.findByText('200,000원')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /방문 요일/ })).toBeNull();
    const days = screen.getAllByRole('button', { name: /^\d+월 \d+일$/ }).filter((b) => !b.disabled);
    fireEvent.click(days[1]);
    fireEvent.click(screen.getByRole('button', { name: '09:00' }));
    expect(screen.getByRole('button', { name: '09:00 ~ 11:00 (2시간)' })).toBeInTheDocument();
  });
});
