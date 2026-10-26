// 날짜·시간 화면(BookingDateTime)의 예약 변경 모드(?edit=) 테스트.
// 지난번에 저장하지 않고 나간 선택이 초안에 남아 있어도, 내 예약 카드가 넘긴 현재 일정으로 다시 채우는지,
// '변경 완료' 가 실패하면 실패 문구가 이 화면의 .error 모양으로 그려지는지 본다.
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { act, render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingDateTime from './BookingDateTime.jsx';
import styles from './BookingDateTime.module.css';
import { __fake } from '../../api/http.js';

// 시간표(GET /catalog/time-slots)는 가짜 서버(test/fakeHttp.js)가 준다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/DateTime 예약 변경', () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => vi.useRealTimers());

  it.each(['10월 24일', '10월 25일'])('%s: 주말 날짜와 방문 시간을 선택할 수 있다', async (label) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-22T11:00:00+09:00'));
    render(
      <MemoryRouter initialEntries={['/booking/home/datetime']}>
        <Routes><Route path="/booking/:category/datetime" element={<BookingDateTime />} /></Routes>
      </MemoryRouter>,
    );
    const day = await screen.findByRole('gridcell', { name: label });
    expect(day).toBeEnabled();
    fireEvent.click(day);
    fireEvent.click(screen.getByRole('radio', { name: '14:00' }));
    expect(day).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
    expect(screen.queryByText(/주말은 예약할 수 없어요/)).toBeNull();
  });

  it.each(['home', 'office', 'appliance', 'move-in'])('%s: 초안 없이 들어와 오늘 날짜를 직접 선택할 수 있다', async (category) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-22T14:40:00+09:00'));
    render(
      <MemoryRouter initialEntries={[`/booking/${category}/datetime`]}>
        <Routes><Route path="/booking/:category/datetime" element={<BookingDateTime />} /></Routes>
      </MemoryRouter>,
    );
    const today = await screen.findByRole('gridcell', { name: '10월 22일' });
    expect(today).toBeEnabled();
    fireEvent.click(today);
    expect(today).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('radio', { name: '17:00' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '18:00' })).toBeEnabled();
    fireEvent.click(screen.getByRole('radio', { name: '18:00' }));
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
  });

  it('당일 3시간 미만 버튼과 만료된 선택의 다음 버튼을 비활성화한다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-22T11:00:00+09:00'));
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ date: '2026-10-22', time: '14:00' }));
    render(
      <MemoryRouter initialEntries={['/booking/home/datetime']}>
        <Routes><Route path="/booking/:category/datetime" element={<BookingDateTime />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByRole('radio', { name: '13:00' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '14:00' })).toBeEnabled();
    expect(screen.getByRole('gridcell', { name: '10월 22일', selected: true })).toBeEnabled();
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
    act(() => {
      vi.setSystemTime(new Date('2026-10-22T11:00:01+09:00'));
      window.dispatchEvent(new Event('focus'));
    });
    expect(screen.getByRole('radio', { name: '14:00' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '14:00' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.click(screen.getByRole('radio', { name: '15:00' }));
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
  });

  // 준비: 예약 r1 의 변경 초안(edit-r1)에 15:00 을 넣어 두고, 화면은 현재 일정 10:00 을 location.state 로 받아 연다.
  // 확인: 시간 버튼 중 10:00 이 선택(aria-checked=true)이고 15:00 은 아니다
  it('저장하지 않고 나간 지난 선택이 남아 있어도 들어올 때마다 실제 현재 일정을 채운다 (자체 점검 #36)', async () => {
    sessionStorage.setItem('ansimclean.booking.edit-r1', JSON.stringify({ date: '2026-11-12', time: '15:00' })); // 지난번에 고르고 그냥 나갔다
    render(
      <MemoryRouter initialEntries={[{ pathname: '/booking/home/datetime', search: '?edit=r1', state: { date: '2026-11-05', time: '10:00' } }]}>
        <Routes>
          <Route path="/booking/:category/datetime" element={<BookingDateTime />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByRole('radio', { name: '10:00' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: '15:00' })).toHaveAttribute('aria-checked', 'false');
  });

  // 예약 변경 요청이 통신 오류로 한 번 실패하면 버튼 아래 실패 문구가 이 화면의 .error(위 여백·가운데 정렬)로 그려진다
  it("'변경 완료' 가 실패하면 실패 문구를 화면의 .error 모양(위 여백·가운데 정렬)으로 보인다", async () => {
    __fake.failOnce.add('PATCH /customer/reservations/r1/schedule');
    render(
      <MemoryRouter initialEntries={[{ pathname: '/booking/home/datetime', search: '?edit=r1', state: { date: '2026-11-05', time: '10:00' } }]}>
        <Routes>
          <Route path="/booking/:category/datetime" element={<BookingDateTime />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '변경 완료' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('네트워크 연결을 확인해주세요');
    expect(alert).toHaveClass(styles.error);
  });
});
