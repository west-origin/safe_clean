import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import useBookingAvailability, { bookingAvailability } from './useBookingAvailability.js';

const slots = { am: ['09:00', '10:00', '11:00'], pm: ['13:00', '14:00', '15:00', '17:00', '18:00'], minimumLeadHours: 3, bookableDays: 60 };
const now = new Date('2026-10-22T11:00:00+09:00');
const availability = (date, time, at = now, category = 'home') => bookingAvailability(slots, date, time, category, undefined, at);
afterEach(() => vi.useRealTimers());

describe('청소 예약 가능 시간', () => {
  it('서울 기준 당일 3시간 미만은 막고 정확히 3시간부터 허용한다', () => {
    expect(availability('2026-10-22', '13:00').canContinue).toBe(false);
    expect(availability('2026-10-22', '14:00').canContinue).toBe(true);
    expect(availability('2026-10-22', '15:00').canContinue).toBe(true);
    expect(availability('2026-10-22', '14:00', new Date(now.getTime() + 1)).canContinue).toBe(false);
  });
  it('지난 날짜·기간 밖·시간표 밖·시간 미선택은 계속 막는다', () => {
    for (const [date, time] of [['2026-10-21', '18:00'], ['2027-01-04', '14:00'], ['2026-10-23', '03:00'], ['2026-10-23', undefined]]) {
      expect(availability(date, time).canContinue).toBe(false);
    }
    expect(availability('2026-10-23', '09:00').canContinue).toBe(true);
  });
  it('토·일도 예약 가능하고 주말 당일은 3시간 간격을 지킨다', () => {
    expect(availability('2026-10-24', '14:00').canContinue).toBe(true);
    expect(availability('2026-10-25', '14:00').canContinue).toBe(true);
    const saturday = new Date('2026-10-24T11:00:00+09:00');
    expect(availability('2026-10-24', '13:00', saturday).canContinue).toBe(false);
    expect(availability('2026-10-24', '14:00', saturday).canContinue).toBe(true);
    expect(availability('2026-10-25', '14:00', saturday, 'repair').canContinue).toBe(true);
  });
  it('서울 자정 경계와 기기 시간대에 상관없이 남은 시간을 계산한다', () => {
    const late = new Date('2026-10-22T20:00:00-07:00'); // 서울 10/23 12시
    expect(availability('2026-10-23', '14:00', late).canContinue).toBe(false);
    expect(availability('2026-10-23', '15:00', late).canContinue).toBe(true);
    expect(availability('2026-10-23', '09:00', new Date('2026-10-22T23:30:00+09:00')).canContinue).toBe(true);
  });
  it('남은 시간이 없으면 오늘 날짜도 막고 수리의 기존 당일 금지를 유지한다', () => {
    expect(availability('2026-10-22', '18:00', new Date('2026-10-22T16:00:00+09:00')).isDateDisabled(new Date(2026, 9, 22))).toBe(true);
    expect(availability('2026-10-22', '18:00', now, 'repair').canContinue).toBe(false);
  });
  it('열어둔 화면에서 시간이 지나거나 앱에 복귀하면 선택이 만료된다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
    const { result } = renderHook(() => useBookingAvailability(slots, '2026-10-22', '14:00', 'home'));
    expect(result.current.canContinue).toBe(true);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.canContinue).toBe(false);
    expect(result.current.isTimeDisabled('14:00')).toBe(true);
    expect(result.current.isStillAvailable()).toBe(false);
    act(() => { vi.setSystemTime(new Date('2026-10-22T15:01:00+09:00')); window.dispatchEvent(new Event('focus')); });
    expect(result.current.isTimeDisabled('18:00')).toBe(true);
  });
});
