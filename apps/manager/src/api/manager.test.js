// manager.js 가 화면 값 ↔ 서버 값을 바르게 바꾸는지 검증한다 (요일·반복 문구·날짜 형식, 콜 note 의 null, 연락처 404 → null).
// 서버 대신 test/fakeHttp.js 의 가짜 서버를 쓰고, 가짜 서버가 기록한 요청 본문(__fake.calls)을 들여다본다.
import { describe, it, expect, vi } from 'vitest';
import { addUnavailable, updateDefaultHours, acceptCall, getCalls, getJobContact } from './manager.js';
import { UNSTARTABLE_JOB_ID } from '../test/fakeHttp.js';

// vi.mock: './http.js' 를 가짜 서버 모듈로 바꾼다 (vitest 가 import 보다 먼저 실행되게 끌어올린다)
vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

describe('manager api — 앱 계약 ↔ 서버 변환 (BACKEND_PLAN.md §9.0)', () => {
  // 준비: 화면 형식('2026.09.18', '매주', 빈 사유)으로 추가 → 서버로 간 본문이 ISO 날짜·'weekly'·null 인지,
  // 돌려받은 항목이 앱 표시 모양인지 확인한다
  it('근무 불가 추가는 반복 문구와 날짜 형식을 서버 값으로 바꾼다', async () => {
    // 테스트 안에서 import 하면 가짜로 바뀐 http.js 가 온다 — __fake 는 그 가짜 서버의 상태
    const { __fake } = await import('./http.js');
    const item = await addUnavailable({ date: '2026.09.18', allDay: false, start: '14:00', end: '18:00', repeat: '매주', reason: '' });
    expect(__fake.calls.at(-1).body).toEqual({ date: '2026-09-18', allDay: false, start: '14:00', end: '18:00', repeat: 'weekly', reason: null });
    expect(item).toMatchObject({ date: '9월 18일', label: '개인 일정', time: '14:00 - 18:00', repeat: 'weekly' });
  });

  // 준비: 월요일 근무 행 + 토요일 휴무 행 → 서버 본문이 weekday 번호(0=월, 5=토)인지, 응답 첫 행(월)이 앱 모양인지 확인한다
  it('기본 근무시간 저장은 요일 행을 weekday 로 바꾸고 앱 행 모양으로 돌려받는다', async () => {
    const { __fake } = await import('./http.js');
    const rows = await updateDefaultHours([{ day: '월', start: '10:00', end: '17:00' }, { day: '토', off: true }]); // 화면이 start/end 원시값을 준다
    expect(__fake.calls.at(-1).body).toEqual([{ weekday: 0, start: '10:00', end: '17:00', off: false }, { weekday: 5, start: null, end: null, off: true }]);
    expect(rows[0]).toEqual({ day: '월', hours: '10:00 - 17:00', off: false, start: '10:00', end: '17:00' });
  });

  // 준비: 가짜 서버의 첫 콜 note 를 null 로 → 목록에서 note 키는 남고 값이 undefined 인지, 콜 수락 응답이 { jobId } 인지 확인한다
  // ('빼고' = 값이 undefined 가 된다는 뜻. CallCard 의 note 는 선택 prop 이라 undefined 면 그리지 않는다)
  it('콜 목록의 note 가 null 이면 빼고, 수락은 { jobId } 를 준다', async () => {
    const { __fake } = await import('./http.js');
    __fake.offers[0].note = null;
    const { calls } = await getCalls();
    expect('note' in calls[0] && calls[0].note === undefined).toBe(true);
    expect(await acceptCall('call-2')).toEqual({ jobId: 'job-1' });
  });

  // 가짜 서버는 UNSTARTABLE_JOB_ID(오늘이 아닌 작업)의 연락처를 404 로 준다 → getJobContact 가 오류 대신 null 을 돌려주는지 본다
  it('연락처 404 는 null (작업 당일이 아니면 버튼 비활성)', async () => {
    expect(await getJobContact(UNSTARTABLE_JOB_ID)).toBeNull();
    expect(await getJobContact('job-1')).toMatchObject({ phone: '010-0000-0000' });
  });
});
