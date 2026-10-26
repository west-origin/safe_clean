// 테스트용 가짜 서버 (매니저). `vi.mock('../../api/http.js', () => fakeHttpModule())`. 응답 모양은 backend/docs/api.md §5.3, 값은 mock.js(Figma 문구)
// 쓰는 곳: 매니저 앱 테스트들이 vi.mock 으로 api/http.js 를 이 모듈로 바꿔 끼운다 (예: api/manager.test.js,
// screens/call/CallList.test.jsx). 매니저 경로(§5.3) 말고도 인증·라이브·지원 경로를 흉내 낸다.
// 개발 중 `VITE_API_MOCK=1` 이면 http.js 가 이 파일의 http 를 불러 서버 없이 화면을 띄운다.
// 흐름: http.get/post/… → call() 이 요청을 state.calls 에 적고, exact(경로 전체가 같은 것) → patterns(정규식)
// 순서로 처리 함수를 찾아 응답을 만든다 (그 앞의 failOnce·override 는 call() 참고). 없는 경로는 404 로 던진다.
// 테스트는 `const { __fake } = await import('../../api/http.js')` 로 상태를 꺼내 값을 바꾸거나 보낸 요청을 확인한다.
// 실제 서버 규칙을 다 흉내 내지는 않는다 — 예: 같은 멱등 키의 재요청 처리, 영상 차단 전 작업 일시정지 거절.
import {
  MANAGER_TERMS, MANAGER_TERM_BODIES, MANAGER_PRIVACY_NOTE, HOME, CALLS, CALL_DETAIL, REJECT_REASONS, JOB_DETAIL, EQUIPMENT_OK, ACTIVE_JOB, PAUSE_REASONS,
  PAUSED_JOB, COMPLETED_JOB, SETTLEMENT, SETTLEMENT_DETAIL, SCHEDULE, SCHEDULE_WEEK, AVAILABILITY, PROFILE, EQUIPMENT_DEVICE, JOB_CONTACT, PROFILE_DETAILS,
  MANAGER_NOTIFICATION_SETTINGS, MANAGER_NOTIFICATION_ROWS,
  MANAGER_NOTICES,
} from '../api/mock.js';

/**
 * http.js 의 ApiError 와 같은 모양 (status 0 = 네트워크 실패). vi.mock 으로 http.js 가 통째로 바뀌므로
 * manager.js 의 `e instanceof ApiError` 도 테스트 중에는 이 클래스를 본다.
 * DEV(`VITE_API_MOCK=1`)에서는 http.js 가 바뀌지 않으므로, http.js 의 가짜 분기가 이 오류를 http.js 의 ApiError 로
 * 바꿔 던진다 — 그래야 예: getJobContact 의 404 → null, 가입 인증번호의 PHONE_TAKEN 안내가 실제 서버일 때처럼 처리된다.
 * http.js 를 import 해 그 클래스를 쓰지 않는 것은 순환을 피하려서다 (테스트에서는 http.js 가 이 모듈로 바뀐다).
 * name 을 'ApiError' 로 두어야 http.js 가 알아본다.
 */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** 가짜 로그인 사용자의 id (user() 가 쓴다) */
export const FAKE_USER_ID = '22222222-2222-2222-2222-222222222222';
/** 휴대폰 인증번호 확인(POST /auth/phone/verify)에서 통과하는 번호. 다른 번호는 OTP_INVALID */
export const VALID_CODE = '123456';
/** 비밀번호 재설정: 이 비밀번호로 보내면 다른 요청이 토큰을 먼저 소비한 것처럼 OTP_EXPIRED (감사 H-01) */
export const RACED_RESET_PASSWORD = 'raced-password1';
/** 이 비밀번호로 재설정하면 서버가 비밀번호는 바꿨는데 새 세션 발급만 실패한 것처럼 PASSWORD_CHANGED (자체 점검 #22) */
export const SESSION_LOST_RESET_PASSWORD = 'session-lost1';
/** 이 번호로 가입 인증번호를 요청하면 PHONE_TAKEN (이미 가입한 번호) */
export const TAKEN_PHONE = '00000000';
export const UNKNOWN_PHONE = '99999999'; // 비밀번호 재설정에서 '가입되지 않은 번호'
/** 로그인·회원 탈퇴에서 이 비밀번호면 LOGIN_FAILED. 다른 비밀번호는 모두 통과한다 */
export const WRONG_PASSWORD = 'wrong-password';
/** 이 콜을 수락하거나 거절하면 서버가 OFFER_TAKEN 으로 거부한다 (다른 매니저가 먼저 수락한 콜) */
export const TAKEN_OFFER_ID = 'call-taken';
/** 이 콜을 거절하면 서버가 INVALID_STATE '이미 수락한 콜이에요 …' 로 거부한다 (수락 응답이 끊겨 거절을 누른 경우, 문제 목록 10번) */
export const MY_ACCEPTED_OFFER_ID = 'call-mine';
/** 이 콜을 수락하면 서버가 INVALID_STATE '근무 일정과 맞지 않는 시간이라 …' 로 거부한다 (콜을 받은 뒤 그 시간에 휴무를 넣은 경우, 문제 목록 7번). 콜은 목록에 남는다 */
export const UNAVAILABLE_OFFER_ID = 'call-unavailable';
/** 이 작업은 오늘이 아니라 시작할 수 없다 (INVALID_STATE) */
export const UNSTARTABLE_JOB_ID = 'job-tomorrow'; // 방문 취소는 409(4시간 전), 고객 연락처는 404 로도 쓴다
/** 수락하면 이동하는 작업 id */
export const ACCEPTED_JOB_ID = 'job-1';

// 가짜 서버에서 weekday 0 = 월 … 6 = 일 (근무시간 저장 때 요일 이름을 만든다)
const DAYS = ['월', '화', '수', '목', '금', '토', '일'];
// 약관 이름 앞의 '(필수) '·'(선택) ' 을 뗀다 — 약관 본문 응답의 title 용
const stripPrefix = (label) => label.replace(/^\((필수|선택)\)\s*/, '');
// 가짜 로그인 사용자. patch 로 이름·가입 단계 같은 필드만 바꾼다
const user = (patch) => ({ id: FAKE_USER_ID, role: 'manager', name: '김매니저', profileComplete: true, signupStep: null, ...patch });
// 로그인·가입·비밀번호 재설정 응답 모양 (토큰 두 개 + 사용자)
const sessionOf = (u) => ({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 3600, user: u });
// '2026-09-18' → '9월 18일' (근무 불가 목록의 날짜 표시)
const dateShort = (iso) => {
  const [, m, d] = iso.split('-').map(Number);
  return `${m}월 ${d}일`;
};

/**
 * 가짜 http.js 모듈을 만든다. 부를 때마다 새 상태(state)를 가진 가짜 서버가 하나 생긴다.
 * 돌려주는 것: { ApiError, newIdempotencyKey, http, __fake } — 앞의 셋은 http.js 가 내보내는 이름과 같다.
 * 한 테스트 파일 안에서는 vi.mock 이 한 번만 만들어 여러 테스트가 같은 가짜 서버를 같이 쓴다. 그래서 값을 바꾼
 * 테스트는 끝에 되돌리거나(예: Settlement.test 의 override = null) 다음 테스트가 시작할 때 다시 정한다.
 * DEV 에서는 http.js 가 처음 요청 때 한 번만 만들어, 새로고침 전까지 상태가 이어진다.
 */
export function fakeHttpModule() {
  // 가짜 서버의 기억. 테스트가 __fake 로 직접 읽고 바꾼다.
  // 여기 없는 값(예: equipment·override)은 테스트가 필요할 때 넣는다 — 읽는 곳 옆에 `__fake.… =` 주석이 있다.
  const state = {
    user: user(),
    calls: [], // 보낸 요청 { method, path, body, idempotencyKey… } 기록 — 테스트가 요청 본문·멱등 키를 확인한다
    inquiries: [], // 접수된 문의 내용(문자열)
    cancelledJobs: [], // 방문 취소 요청 { id, reason, detail } 기록
    homeManager: {}, // __fake.homeManager = { suspension: '…' } → 홈 매니저 필드 덮어쓰기
    jobDetail: {}, // __fake.jobDetail = { status: 'inProgress' } → 업무 상세 필드 덮어쓰기
    jobError: null, // __fake.jobError = { status: 409, code: 'INVALID_STATE', message: '취소된 예약이에요' } → 업무 상세(GET /manager/jobs/:id)가 이 오류로 실패
    scheduleItems: null, // __fake.scheduleItems = [...] → 일정 목록 덮어쓰기 (고객의 취소·일정 변경 흉내). null 이면 mock.js 그대로
    failOnce: new Set(), // '메서드 경로' 를 넣으면 그 요청만 한 번 NETWORK 로 실패 (call() 참고)
    /** 매니저 정책 (docs/manager-policy.md) */
    penalties: { points: '1', nextThreshold: '3점이 되면 경고 알림을 보내드려요', suspension: null, items: [{ id: 'pen-1', title: '방문 취소 (24시간 이내)', points: '+1점', date: '9월 20일 오전 9:00', status: '적용 중', detail: null }] },
    pushTokens: [], // PUT /push/token 으로 등록된 { platform, token }
    /** 콜 목록 (서버 모양, note 는 없으면 null) */
    offers: CALLS.calls.map((c) => ({ ...c, expiresAt: new Date(Date.now() + 272_000).toISOString() })), // 4:32 뒤 만료 (화면이 카운트다운을 그린다)
    // 진행 중 작업. checklist 를 항목까지 복사해 둔다 — 체크 토글(i.done = …)이 mock.js 의 원본 상수를 바꾸지 않게
    activeJob: { ...ACTIVE_JOB, startedAt: '2026-09-15T05:02:00Z', pausedSeconds: 0, checklist: ACTIVE_JOB.checklist.map((g) => ({ ...g, items: g.items.map((i) => ({ ...i })) })) },
    // 근무시간·근무 불가도 복사본으로 시작한다. off 는 true/false 로 맞추고, repeat 는 'none' 으로 둔다
    defaultHours: AVAILABILITY.defaultHours.map((r) => ({ ...r, off: Boolean(r.off) })),
    unavailable: AVAILABILITY.unavailable.map((u) => ({ ...u, repeat: 'none' })),
    notifications: { ...MANAGER_NOTIFICATION_SETTINGS }, // 알림 설정 켜짐/꺼짐 (PUT /manager/notifications/:key 가 바꾼다)
    /** 라이브 세션 상태 (start 뒤 채워진다). livePauseFails=true 면 pause 가 502 */
    live: null,
    livePauseFails: false,
    startFails: false, // true 면 라이브 시작이 409 INVALID_STATE (바디캠 미등록 문구)
    /** 청소 전·후 사진 [{ id, location, beforeUrl, afterUrl }] — __fake.jobPhotos = [...] 로 미리 채운다 */
    jobPhotos: [],
    photoPhase: 'live', // 기존 진행 중 작업 fixture. 새 작업은 start에서 before로 바뀐다.
    requiredRooms: ['거실', '주방', '욕실'],
  };
  // 올린 사진 파일의 주소: 브라우저(VITE_API_MOCK=1)에서는 찍은 사진을 그대로 보이게 blob: 주소, 테스트(jsdom)에는 없어 예시 주소
  const photoUrl = (file, name) => globalThis.URL?.createObjectURL?.(file) ?? `https://example.com/photos/${name}.jpg`;
  const photoOf = (id) => state.jobPhotos.find((p) => p.id === id) ?? (() => { throw notFound(id); })();
  // 404 NOT_FOUND 오류를 만든다 (what = 찾지 못한 것, 문구에 붙는다)
  const notFound = (what) => new ApiError(404, 'NOT_FOUND', `찾을 수 없어요: ${what}`);
  // 지금 목록에 있는 콜을 id 로 찾는다. 없으면(이미 수락·거절돼 빠졌으면) 404
  const offerOf = (id) => {
    const o = state.offers.find((x) => x.id === id);
    if (!o) throw notFound(id);
    return o;
  };

  // 경로가 글자 그대로 같을 때 쓰는 응답표. 키는 '메서드 경로' (쿼리 문자열까지 포함).
  // 처리 함수는 (본문, 옵션) 을 받아 응답을 돌려주거나, 서버의 오류 응답처럼 ApiError 를 던진다.
  const exact = {
    'GET /terms?audience=manager': () => ({ terms: MANAGER_TERMS, note: MANAGER_PRIVACY_NOTE }),
    'POST /auth/login': (b) => {
      if (b.password === WRONG_PASSWORD) throw new ApiError(401, 'LOGIN_FAILED', '휴대폰 번호 또는 비밀번호를 확인해주세요');
      return sessionOf(state.user);
    },
    'POST /auth/phone/otp': (b) => {
      if (b.phone === TAKEN_PHONE) throw new ApiError(409, 'PHONE_TAKEN', '이미 가입한 번호예요. 로그인해서 이어서 진행해주세요');
      return { sent: true, expiresIn: 300 };
    },
    'POST /auth/password/otp': (b) => {
      if (b.phone === UNKNOWN_PHONE) throw new ApiError(404, 'PHONE_NOT_FOUND', '가입되지 않은 번호예요');
      return { sent: true, expiresIn: 300, resendAfter: 60 };
    },
    // 인증 토큰은 POST /auth/phone/verify 가 준 'tok' 만 유효하다 (가입도 같다)
    'POST /auth/password/reset': (b) => {
      if (b.verificationToken !== 'tok' || b.password === RACED_RESET_PASSWORD) throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 다시 해주세요');
      if (b.password === SESSION_LOST_RESET_PASSWORD) throw new ApiError(409, 'PASSWORD_CHANGED', '비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요');
      state.user = user({ profileComplete: true });
      return sessionOf(state.user);
    },
    'POST /auth/phone/verify': (b) => {
      if (b.code !== VALID_CODE) throw new ApiError(400, 'OTP_INVALID', '인증번호가 맞지 않아요');
      return { verificationToken: 'tok', expiresIn: 900 };
    },
    // 가입 직후는 아직 가입 완료(약관 동의 전송, 아래 signup/complete) 전이다 — profileComplete=false,
    // signupStep='complete'(다음에 이어갈 화면 키. manager.js SIGNUP_STEP_PATHS 참고)
    'POST /auth/signup': (b) => {
      if (b.verificationToken !== 'tok') throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 다시 해주세요');
      state.user = user({ name: b.name, profileComplete: false, signupStep: 'complete' });
      return sessionOf(state.user);
    },
    // 필수 약관 id 가 전부 들어 있어야 가입을 마친다
    'POST /auth/signup/complete': (b) => {
      const required = MANAGER_TERMS.filter((t) => t.required).map((t) => t.id);
      if (!required.every((id) => (b.agreedTermIds ?? []).includes(id))) throw new ApiError(400, 'VALIDATION', '필수 약관에 동의해주세요');
      state.user = { ...state.user, profileComplete: true, signupStep: null };
      return { profileComplete: true, user: state.user };
    },
    'GET /manager/cancel-reasons': () => [
      { key: 'sick', label: '질병·부상' },
      { key: 'accident', label: '교통사고·차량 문제' },
      { key: 'equipment', label: '장비 고장' },
      { key: 'personal', label: '개인 사정' },
      { key: 'other', label: '기타' },
    ],
    'GET /manager/penalties': () => state.penalties,
    'GET /auth/me': () => state.user,
    'POST /auth/logout': () => ({ ok: true }),
    'GET /support/notices?audience=manager': () => MANAGER_NOTICES.map(({ id, title, date }) => ({ id, title, date })),
    // 접수된 문의를 목록 모양으로 만든다. 첫 문의만 답변 완료, 나머지는 답변 대기
    'GET /support/inquiries': () => state.inquiries.map((content, i) => ({ id: `inq-${i + 1}`, content, status: i === 0 ? 'answered' : 'open', answer: i === 0 ? '확인했어요. 곧 도와드릴게요' : null, createdLabel: '9월 15일 오후 2:00', answeredLabel: i === 0 ? '9월 16일 오전 10:00' : null })),
    'POST /support/inquiries': (b) => {
      if (!b.content?.trim()) throw new ApiError(400, 'VALIDATION', '문의 내용을 입력해주세요');
      state.inquiries.push(b.content);
      return { id: `inq-${state.inquiries.length}` };
    },
    'PUT /push/token': (b) => {
      state.pushTokens.push(b);
      return { ok: true };
    },
    'DELETE /push/token': (b) => {
      state.pushTokens = state.pushTokens.filter((t) => t.token !== b.token);
      return { ok: true };
    },
    'DELETE /auth/account': (b) => {
      if (b.password === WRONG_PASSWORD) throw new ApiError(401, 'LOGIN_FAILED', '휴대폰 번호 또는 비밀번호를 확인해주세요');
      return null; // 실제 서버는 { ok: true } 를 주지만 manager.js 의 deleteAccount 는 응답 본문을 쓰지 않는다
    },
    // ── B4 매니저 ──
    // 홈: 남은 콜 수로 newCalls.count 를 채우고, 콜이 하나도 없으면 newCalls 는 null
    'GET /manager/home': () => ({ ...HOME, manager: { ...HOME.manager, ...state.homeManager }, newCalls: state.offers.length ? { ...HOME.newCalls, count: state.offers.length } : null }),
    'GET /manager/offers': () => ({ tabBadge: state.offers.length, calls: state.offers, availability: CALLS.availability }),
    'GET /catalog/reasons?kind=reject': () => REJECT_REASONS,
    'GET /catalog/reasons?kind=pause': () => PAUSE_REASONS,
    // 진행 중 작업이 없으면(complete 뒤 null) 404.
    // (() => { throw … })() = 식 한가운데서 오류를 던지려고 만들자마자 부르는 함수
    'GET /manager/jobs/active': () => state.activeJob ?? (() => { throw notFound('active job'); })(),
    'GET /manager/jobs/active/pause': () => (state.pausedJob === null ? (() => { throw notFound('paused job'); })() : PAUSED_JOB), // __fake.pausedJob = null → 작업이 이미 진행 중
    'GET /manager/jobs/last-completed': () => COMPLETED_JOB,
    'GET /manager/device/status': () => state.equipment ?? EQUIPMENT_OK, // __fake.equipment 로 장비 점검 결과를 바꾼다 (없으면 정상)
    'GET /manager/device': () => ({ ...EQUIPMENT_DEVICE, stream: { url: 'rtmp://example.livekit.cloud/x', keyMasked: '****-**-1234' } }),
    'GET /manager/settlements': () => ({ ...SETTLEMENT, nextBefore: null }), // 다음 페이지 없음. 페이지 넘김은 테스트가 override 로 흉내 낸다
    'GET /manager/schedule': () => ({ items: state.scheduleItems ?? SCHEDULE.items, week: SCHEDULE_WEEK }), // 쿼리(?from=…&to=…)가 붙으면 아래 patterns 가 받는다
    'GET /manager/availability': () => ({ defaultHours: state.defaultHours, unavailable: state.unavailable }),
    // 본문은 [{ weekday, off, start, end }] 배열. 보낸 요일만 고치고 전체 목록을 돌려준다
    'PUT /manager/availability/hours': (rows) => {
      for (const r of rows) {
        if (r.weekday < 0 || r.weekday > 6) throw new ApiError(400, 'VALIDATION', 'weekday 는 0..6');
        state.defaultHours[r.weekday] = { day: DAYS[r.weekday], hours: r.off ? '휴무' : `${r.start} - ${r.end}`, off: Boolean(r.off), start: r.off ? null : r.start, end: r.off ? null : r.end };
      }
      return state.defaultHours;
    },
    // 날짜 형식·반복 값을 검사하고, 목록 표시용 항목(날짜 '9월 18일', 시간 '하루 종일' 등)을 만들어 붙인다
    'POST /manager/availability/unavailable': (b) => {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(b.date ?? '')) throw new ApiError(400, 'VALIDATION', 'date 는 YYYY-MM-DD 형식이에요');
      if (!['none', 'weekly', 'monthly'].includes(b.repeat)) throw new ApiError(400, 'VALIDATION', 'repeat 는 none, weekly, monthly 중 하나예요');
      const item = { id: `un-${state.unavailable.length + 1}`, date: dateShort(b.date), label: b.reason || (b.allDay ? '휴무' : '개인 일정'), time: b.allDay ? '하루 종일' : `${b.start} - ${b.end}`, repeat: b.repeat };
      state.unavailable.push(item);
      return item;
    },
    'GET /manager/profile': () => PROFILE,
    'GET /manager/notifications': () => ({ rows: MANAGER_NOTIFICATION_ROWS, values: { ...state.notifications } }),
  };
  // 경로에 id 가 들어가는 요청. [메서드, 정규식, 처리 함수] — 정규식 괄호로 잡은 값들이 첫 인자 배열로 온다.
  // exact 를 먼저 보므로 '/manager/jobs/active' 는 아래 '/manager/jobs/:id' 에 id='active' 로 걸리지 않는다.
  const patterns = [
    ['GET', /^\/support\/notices\/([^?]+)\?audience=manager$/, ([id]) => {
      const n = MANAGER_NOTICES.find((x) => x.id === id);
      if (!n) throw new ApiError(404, 'NOT_FOUND', '찾을 수 없어요');
      return { ...n };
    }],
    ['GET', /^\/terms\/([^?]+)\?audience=manager$/, ([id]) => {
      const term = MANAGER_TERMS.find((t) => t.id === id);
      if (!term) throw notFound(id);
      return { id: term.id, title: stripPrefix(term.label), body: MANAGER_TERM_BODIES[term.id] };
    }],
    ['DELETE', /^\/manager\/availability\/unavailable\/([^/]+)$/, ([id]) => {
      state.unavailable = state.unavailable.filter((u) => u.id !== id);
      return null;
    }],
    ['GET', /^\/manager\/offers\/([^/]+)$/, ([id]) => ({ ...CALL_DETAIL, ...offerOf(id) })], // 콜 상세 = CALL_DETAIL 위에 목록의 그 콜을 덮는다
    // 매니저 방문 취소 (docs/manager-policy.md) — 예약은 취소되지 않고 다시 매칭된다
    ['POST', /^\/manager\/jobs\/([^/]+)\/cancel$/, ([id], b) => {
      if (!b.reason) throw new ApiError(400, 'VALIDATION', '취소 사유를 선택해주세요');
      if (id === UNSTARTABLE_JOB_ID) throw new ApiError(409, 'INVALID_STATE', '방문 4시간 전부터는 앱에서 취소할 수 없어요. 고객센터로 전화해주세요');
      state.cancelledJobs.push({ id, ...b });
      return { id, status: 'assigning' };
    }],
    // 수락을 시도한 콜은 성공·실패(OFFER_TAKEN) 모두 목록에서 뺀다 — 목록을 다시 받아 오면 마감된 콜이 빠져 있다.
    // 근무 일정과 맞지 않아 거부된 콜(UNAVAILABLE_OFFER_ID)은 서버처럼 대기 중으로 남긴다
    ['POST', /^\/manager\/offers\/([^/]+)\/accept$/, ([id]) => {
      offerOf(id);
      if (id === UNAVAILABLE_OFFER_ID) throw new ApiError(409, 'INVALID_STATE', '근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요');
      state.offers = state.offers.filter((o) => o.id !== id);
      if (id === TAKEN_OFFER_ID) throw new ApiError(409, 'OFFER_TAKEN', '다른 매니저가 먼저 수락했어요');
      return { jobId: ACCEPTED_JOB_ID };
    }],
    // 거절도 서버처럼 닫힌 콜은 오류로 답한다(api.md rejectCall): 다른 매니저가 먼저 수락한 콜 = OFFER_TAKEN, 내가 수락한 콜 = INVALID_STATE.
    // 어느 쪽이든 그 콜은 목록에서 뺀다 — 서버 목록은 대기 중인 콜만 싣는다
    ['POST', /^\/manager\/offers\/([^/]+)\/reject$/, ([id], b) => {
      if (!b.reason) throw new ApiError(400, 'VALIDATION', 'reason: 거절 사유를 골라주세요');
      state.offers = state.offers.filter((o) => o.id !== id);
      if (id === TAKEN_OFFER_ID) throw new ApiError(409, 'OFFER_TAKEN', '다른 매니저가 먼저 수락했어요');
      if (id === MY_ACCEPTED_OFFER_ID) throw new ApiError(409, 'INVALID_STATE', '이미 수락한 콜이에요. 일정에서 확인해주세요');
      return { id, reason: b.reason };
    }],
    // UNSTARTABLE_JOB_ID 는 연락처가 없는 경우(404)로 쓴다 — manager.js 의 getJobContact 는 404 를 null 로 바꾼다
    ['GET', /^\/manager\/jobs\/([^/]+)\/contact$/, ([id]) => (id === UNSTARTABLE_JOB_ID ? (() => { throw notFound('contact'); })() : { ...JOB_CONTACT })],
    // 업무 상세: UNSTARTABLE_JOB_ID 는 내일 작업(today=false), 나머지는 JOB_DETAIL 위에 __fake.jobDetail 을 덮는다
    ['GET', /^\/manager\/jobs\/([^/]+)$/, ([id]) => (state.jobError ? (() => { throw new ApiError(state.jobError.status, state.jobError.code, state.jobError.message); })() : id === UNSTARTABLE_JOB_ID ? { ...JOB_DETAIL, id, today: false, startLabel: '9월 16일 (수) 10:00 시작', scope: [] } : { ...JOB_DETAIL, id, ...state.jobDetail })],
    ['POST', /^\/manager\/jobs\/([^/]+)\/start$/, ([id]) => {
      if (id === UNSTARTABLE_JOB_ID) throw new ApiError(409, 'INVALID_STATE', '작업 당일에만 시작할 수 있어요');
      state.photoPhase = 'before';
      return { jobId: id, startedAt: '2026-09-15T05:02:00Z' };
    }],
    // 작업 일시정지(② 단계). ① 은 라이브 영상 차단(POST /live/sessions/:id/pause)이다.
    // 실제 서버는 영상이 아직 나갈 수 있으면 409 "영상을 먼저 중단해주세요" 로 거절하지만, 가짜 서버는 ① 을 확인하지 않는다
    ['POST', /^\/manager\/jobs\/([^/]+)\/pause$/, ([id], b) => {
      if (state.jobPauseFails) throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요'); // __fake.jobPauseFails = true → ② 실패
      if (state.jobPauseLost) { // __fake.jobPauseLost = true → 서버에선 일시정지됐는데 응답만 잃었다
        if (state.activeJob) state.activeJob.status = 'paused';
        throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요');
      }
      return { jobId: id, reason: b.reason, since: '14:32부터 중단됨' };
    }],
    ['POST', /^\/manager\/jobs\/([^/]+)\/resume$/, ([id]) => ({ jobId: id })],
    ['POST', /^\/manager\/jobs\/([^/]+)\/complete$/, ([id]) => {
      state.activeJob = null; // 끝나면 진행 중 작업이 없어진다 (GET /manager/jobs/active 가 404)
      return { jobId: id };
    }],
    // 체크리스트 한 항목의 done 을 바꾼다 (진행 중 작업의 모든 묶음에서 같은 id 를 찾는다)
    ['PUT', /^\/manager\/jobs\/([^/]+)\/checklist\/([^/]+)$/, ([jobId, itemId], b) => {
      state.activeJob?.checklist.forEach((g) => g.items.forEach((i) => { if (i.id === itemId) i.done = Boolean(b.done); }));
      return { jobId, itemId, done: Boolean(b.done) };
    }],
    // 청소 전·후 사진 (본문은 FormData — location·file). 서버의 진행 중 작업·10곳 검사는 흉내 내지 않는다
    ['GET', /^\/manager\/jobs\/([^/]+)\/photos$/, () => ({ photos: state.jobPhotos.map((p) => ({ ...p })), max: 10, phase: state.photoPhase, requiredRooms: [...state.requiredRooms] })],
    ['POST', /^\/manager\/jobs\/([^/]+)\/photos\/confirm-before$/, () => {
      if (state.requiredRooms.some((room) => !state.jobPhotos.some((p) => p.location === room))) throw new ApiError(400, 'VALIDATION', '모든 공간의 청소 전 사진을 찍어주세요');
      state.photoPhase = 'live'; return { ok: true };
    }],
    ['POST', /^\/manager\/jobs\/([^/]+)\/finish-video$/, ([id]) => { state.photoPhase = 'after'; return { jobId: id }; }],
    ['POST', /^\/manager\/jobs\/([^/]+)\/rooms$/, ([id], b) => {
      if (!state.requiredRooms.includes(b.name)) {
        state.requiredRooms.push(b.name);
        state.activeJob.checklist.push({ group: b.name, items: [{ id: `room-${state.requiredRooms.length}`, label: '청소 완료', done: false }] });
      }
      return { jobId: id };
    }],
    ['POST', /^\/manager\/jobs\/([^/]+)\/photos$/, (_, b) => {
      const n = state.jobPhotos.length + 1;
      const photo = { id: `photo-${n}`, location: b.get('location'), beforeUrl: photoUrl(b.get('file'), `before-${n}`), afterUrl: null };
      state.jobPhotos.push(photo);
      return { ...photo };
    }],
    ['PUT', /^\/manager\/jobs\/([^/]+)\/photos\/([^/]+)\/after$/, ([, photoId], b) => {
      const photo = photoOf(photoId);
      photo.afterUrl = photoUrl(b.get('file'), `after-${photoId}`);
      return { ...photo };
    }],
    ['DELETE', /^\/manager\/jobs\/([^/]+)\/photos\/([^/]+)$/, ([, photoId]) => {
      photoOf(photoId);
      state.jobPhotos = state.jobPhotos.filter((p) => p.id !== photoId);
      return null;
    }],
    ['GET', /^\/manager\/settlements\/([^/]+)$/, ([id]) => ({ ...SETTLEMENT_DETAIL, id })],
    ['GET', /^\/manager\/schedule\?/, () => ({ items: state.scheduleItems ?? SCHEDULE.items, week: SCHEDULE_WEEK })], // 기간 조회도 기간과 상관없이 같은 일정
    // 프로필 하위 화면 데이터 (key 는 PROFILE_DETAILS 의 키. 없으면 404)
    ['GET', /^\/manager\/profile\/([^/]+)$/, ([key]) => PROFILE_DETAILS[key] ?? (() => { throw notFound(key); })()],
    // ── 라이브 (B5): 바디캠이 바로 붙은 것으로 흉내 낸다 ──
    // 시작하면 state.live 를 채운다. preview 는 가짜 접속 정보(주소·토큰)다
    ['POST', /^\/live\/sessions\/([^/]+)\/start$/, ([id]) => {
      if (state.startFails) throw new ApiError(409, 'INVALID_STATE', '바디캠이 등록되지 않았어요. 운영자에게 문의해주세요');
      state.live = { status: 'live', cameraOnline: true, cameraBusy: false };
      // __fake.liveSource = 'app' → 휴대폰 송출 모드: preview 대신 publish(송출 토큰) (BACKEND_PLAN §8.0)
      if (state.liveSource === 'app') return { roomName: `booking-${id}`, ...state.live, preview: null, publish: { url: 'ws://fake', token: 'publish-token' } };
      return { roomName: `booking-${id}`, ...state.live, preview: { url: 'ws://fake', token: 'preview-token' } };
    }],
    // 세션 상태: 시작 전이면 status 'created'(카메라 꺼짐), 일시정지 중이면 muted=true
    ['GET', /^\/live\/sessions\/([^/]+)$/, () => ({ ...(state.live ?? { status: 'created', cameraOnline: false, cameraBusy: false }), muted: state.live?.status === 'paused', viewers: 0, startedAt: '2026-09-15T05:02:00Z', held: Boolean(state.live?.held), source: state.liveSource ?? 'ingress' })], // __fake.live = { …, held: true } → 서버가 '매니저가 멈춘 채' 라고 답한다
    // 영상 차단(①). livePauseFails 면 502 — 서버가 차단을 확인하지 못한 경우를 흉내 낸다
    ['POST', /^\/live\/sessions\/([^/]+)\/pause$/, () => {
      if (state.livePauseFails) {
        state.live = { ...state.live, held: false }; // 502 = '계속 전송' — 실서버도 멈춤 표시를 지운다
        throw new ApiError(502, 'LIVE_PAUSE_UNCONFIRMED', '촬영을 중단하지 못했어요. 다시 시도해주세요');
      }
      state.live = { ...state.live, status: 'paused', held: true }; // 200 = 매니저가 재개할 때까지 막아 둔다
      return { status: 'paused', muted: true };
    }],
    ['POST', /^\/live\/sessions\/([^/]+)\/resume$/, () => {
      // __fake.liveResumeFailsOnce = true → 한 번만 502 (원격 unmute 가 막힌 LiveKit 프로젝트 흉내 — 휴대폰이 스스로 풀고 다시 부른다, §8.0)
      if (state.liveResumeFailsOnce) {
        state.liveResumeFailsOnce = false;
        throw new ApiError(502, 'LIVE_PAUSE_UNCONFIRMED', '영상을 다시 시작하지 못했어요. 잠시 후 다시 시도해주세요');
      }
      state.live = { ...state.live, status: 'live', held: false };
      return { status: 'live', muted: false };
    }],
    ['POST', /^\/live\/sessions\/([^/]+)\/stop$/, () => {
      if (state.liveStopFails) throw new ApiError(502, 'UPSTREAM', '실시간 영상을 종료하지 못했어요. 다시 시도해주세요');
      state.live = { ...state.live, status: 'ended' };
      return { ok: true };
    }],
    ['PUT', /^\/manager\/notifications\/([^/]+)$/, ([key], b) => {
      state.notifications[key] = Boolean(b.enabled);
      return { key, enabled: Boolean(b.enabled) };
    }],
  ];
  // 가짜 요청 하나. 순서: 기록 → failOnce(한 번 실패) → override(테스트가 강제한 응답) → exact → patterns → 404.
  // async 라 실제 http.js 처럼 응답은 Promise 로, 던진 ApiError 는 거부된 Promise 로 돌아간다.
  const call = async (method, path, body, opts = {}) => {
    state.calls.push({ method, path, body, ...opts });
    if (state.failOnce.has(`${method} ${path}`)) { // __fake.failOnce.add('GET /…') → 그 요청만 한 번 NETWORK 로 실패
      state.failOnce.delete(`${method} ${path}`);
      throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요');
    }
    // __fake.override = (method, path, body) => 응답 또는 undefined — 예: Settlement.test 의 정산 페이지 넘김
    if (state.override) {
      const forced = state.override(method, path, body); // 테스트가 특정 응답을 강제할 때 (undefined 면 평소대로)
      if (forced !== undefined) return forced;
    }
    const handler = exact[`${method} ${path}`];
    if (handler) return handler(body ?? {}, opts);
    for (const [m, re, fn] of patterns) {
      const match = m === method && path.match(re);
      if (match) return fn(match.slice(1), body ?? {}, opts);
    }
    throw new ApiError(404, 'NOT_FOUND', `가짜 서버에 없는 경로: ${method} ${path}`);
  };
  // http.js 와 같은 이름으로 내보낸다. http 의 함수 모양도 같다 (get 은 경로만, 나머지는 경로·본문·옵션)
  return {
    ApiError,
    newIdempotencyKey: () => 'key-1', // 늘 같은 키 — 테스트가 요청에 실린 키를 확인한다. 가짜 서버는 중복 처리를 흉내 내지 않는다
    http: {
      get: (p) => call('GET', p),
      post: (p, b, o) => call('POST', p, b, o),
      put: (p, b, o) => call('PUT', p, b, o),
      patch: (p, b, o) => call('PATCH', p, b, o),
      delete: (p, b, o) => call('DELETE', p, b, o),
    },
    /** 테스트가 호출 기록·상태를 들여다볼 때 */
    __fake: state,
  };
}
