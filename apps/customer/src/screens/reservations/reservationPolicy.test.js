import { describe, it, expect, vi, afterEach } from 'vitest';
import { isVisitToday, canEditReservation } from './reservationPolicy.js';

afterEach(() => vi.useRealTimers());
describe('예약 상태별 버튼', () => {
  it('한국 날짜가 바뀌는 자정을 기준으로 당일을 판단한다', () => {
    expect(isVisitToday({ date: '2026-11-04' }, new Date('2026-11-03T15:00:00Z'))).toBe(true);
    expect(isVisitToday({ date: '2026-11-04' }, new Date('2026-11-03T14:59:59Z'))).toBe(false);
  });
  it('매칭 중에는 기한과 무관하게 표시하고 매칭 완료 당일에는 숨긴다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-04T01:00:00Z'));
    const r = { date: '2026-11-04', changeable: false };
    expect(canEditReservation({ ...r, status: 'assigning' })).toBe(true);
    expect(canEditReservation({ ...r, status: 'scheduled', changeable: true })).toBe(false);
    expect(canEditReservation({ ...r, status: 'inProgress' })).toBe(false);
    expect(canEditReservation({ ...r, date: '2026-11-06', status: 'scheduled', changeable: true })).toBe(true);
  });
});
