// customer.js 의 예약 함수(생성·견적·내 예약 목록·변경·취소)와 기존 주소·카테고리·주소 검색 테스트.
// 진짜 서버 대신 가짜 서버(test/fakeHttp.js)를 쓴다. 가짜 서버 상태는 이 파일의 테스트끼리 이어진다
// (vi.mock 이 모듈을 한 번만 만든다) — 예: 첫 테스트가 'sk' 키로 만든 예약을 둘째 테스트가 다시 쓴다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createBooking, getReservations, cancelReservation, changeReservation, getBookingQuote, getSavedAddress, getBookingCategory, searchAddress } from './customer.js';

vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

// 예약 플로우를 끝까지 채운 초안 (1회 청소, 새 주소 입력, 무료 옵션 1개, 멱등 키 'sk')
const FULL = { frequency: 'oneTime', date: '2026-12-01', time: '10:00', reuseAddress: 'no', roadAddress: '부산광역시 중앙대로 1', detailAddress: '101호', freeOptions: ['dishes'], paidOptions: [], notices: [], request: '', submitKey: 'sk' };

describe('customer api — 예약과 내 예약 (서버 어댑터)', () => {
  beforeEach(() => sessionStorage.clear());

  // 예약을 만들고 → 내 예약 목록에서 그 카드를 찾아 → 일정 변경 → 취소까지. 카드 문구(일정·주소·매니저 행)는 가짜 서버가 서버를 흉내 내 만든다
  it('만든 예약이 내 예약(예정 탭)에 보이고 변경·취소할 수 있다', async () => {
    const booking = await createBooking('home', FULL);
    expect(booking).toMatchObject({ category: 'home', cleaner: '배정 후 알려드려요', totalLabel: '100,000원' });
    const { reservations } = await getReservations();
    const mine = reservations.find((r) => r.id === booking.id);
    expect(mine).toMatchObject({ tab: 'upcoming', status: 'assigning', title: '가정/원룸 청소', category: 'home', series: false });
    expect(mine.rows.map((r) => r.value)).toEqual(['2026년 12월 1일 (화) 오전 10시', '부산광역시 중앙대로 1', '배정 후 알려드려요']);
    const changed = await changeReservation(booking.id, { date: '2026-12-02', time: '14:00' });
    expect(changed.rows[0].value).toBe('2026년 12월 2일 (수) 오후 2시');
    const cancelled = await cancelReservation(booking.id);
    expect(cancelled.tab).toBe('cancelled');
  });

  it('완료 전에 빠진 값은 어댑터가 막고, 멱등 키는 초안의 submitKey 를 보낸다', async () => {
    await expect(createBooking('home', { ...FULL, date: undefined })).rejects.toThrow('방문 일정');
    await expect(createBooking('home', { ...FULL, roadAddress: '' })).rejects.toThrow('주소');
    // 무료 옵션 개수는 어댑터가 아니라 서버(가짜)가 거부한다 — 그래서 요청 기록에 'sk2' 가 남는다
    await expect(createBooking('home', { ...FULL, submitKey: 'sk2', freeOptions: ['a', 'b', 'c'] })).rejects.toThrow('무료 옵션');
    const { __fake } = await import('./http.js');
    expect(__fake.calls.at(-1)).toMatchObject({ method: 'POST', path: '/customer/bookings', idempotencyKey: 'sk2' });
    // 같은 키에 다른 본문 → 서버가 IDEMPOTENCY_MISMATCH
    // ('sk' 는 첫 테스트가 FULL 로 이미 예약을 만든 키다)
    await expect(createBooking('home', { ...FULL, request: '다른 내용' })).rejects.toMatchObject({ code: 'IDEMPOTENCY_MISMATCH' });
  });

  it('견적은 일정이 없으면 서버를 부르지 않고 null, 정기청소는 매월 1회 6회차', async () => {
    expect(await getBookingQuote('home', { frequency: 'recurring' })).toBeNull();
    const q = await getBookingQuote('home', { frequency: 'recurring', date: '2026-11-02', time: '10:00' });
    expect(q.visits).toHaveLength(6); // 매월 1회 × 6개월 (사용자 결정 2026-09-22)
    expect(q.totalLabel).toBe('480,000원'); // 80,000 × 6회
  });

  it('기존 주소·카테고리의 404 는 null 로, 짧은 검색어는 서버 없이 빈 결과', async () => {
    const { __fake } = await import('./http.js');
    expect(await getSavedAddress()).toEqual({ roadAddress: '부산광역시 중앙대로 2127-23', detailAddress: '금정아파트 802동 203호' });
    __fake.hasDefaultAddress = false; // 가짜 서버가 기본 주소 조회에 404 를 주게 바꾼다
    expect(await getSavedAddress()).toBeNull();
    expect(await getBookingCategory('nope')).toBeNull();
    expect((await getBookingCategory('home')).label).toBe('가정/원룸 청소');
    expect(await searchAddress('a')).toEqual({ query: 'a', results: [] });
    expect((await searchAddress('테헤란로')).results).toHaveLength(4); // 가짜 서버는 검색어와 상관없이 mock.js 의 ADDRESS_RESULTS 를 준다
  });
});
