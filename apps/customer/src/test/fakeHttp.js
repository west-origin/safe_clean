// 테스트용 가짜 서버. 화면·api·live 테스트는 아래처럼 http.js 를 이 모듈로 바꾼다
// (vi.mock 은 파일 맨 위로 끌어올려지므로 공장 함수 안에서 이 파일을 불러온다):
//   vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
// 응답 모양은 backend/docs/api.md 를 따르되 화면이 쓰는 필드만 채운다. api.md 와 다른 곳 예: DELETE /auth/account 는
// { ok: true } 대신 null, POST /auth/phone/otp 에는 resendAfter 가 없다 (앱이 60초로 채운다).
// 값은 mock.js(Figma 문구)에서 온다. 서버 규칙 중 화면이 의존하는 것만 흉내 낸다.
// DEV 에서 `VITE_API_MOCK=1` 이면 api/http.js 도 이 파일의 http 만 가져다 써서 서버 없이 화면을 띄운다.
// 흐름: fakeHttpModule() 이 부를 때마다 새 state(예약·주소·받은 요청 기록 …)를 만들고, 요청이 오면
//   exact(메서드 + 경로가 글자 그대로 같은 것) → patterns(경로에 id·검색어가 들어가는 정규식) 순으로 처리 함수를 찾는다.
// 테스트는 돌려받은 모듈의 __fake(= state)를 직접 읽고 바꿔서 서버 상태를 꾸미거나 보낸 요청을 확인한다.
// 한 테스트 파일 안에서는 같은 state 가 이어지므로 필요한 테스트가 직접 비운다 (예: RepairFlow.test 의 __fake.calls.length = 0).
import { fromDateKey } from '@ansimclean/ui';
import {
  TERMS, TERM_BODIES, HOME_SLIDES, REPAIR_HERO, RESERVATIONS, RESERVATIONS_HEADER, RESERVATION_TABS, SETTINGS_HEADER, PROFILE, SETTINGS_GROUPS,
  NOTIFICATION_SETTINGS, BOOKING_CATEGORIES, TIME_SLOTS, BOOKABLE_DAYS, CHANGE_DEADLINE_HOURS, SERIES_TERM_MONTHS, BOOKING_OPTIONS, FREE_OPTION_LIMIT, NOTICE_CHIPS, NOTES_COPY, FREQUENCY_DESCRIPTIONS,
  CONFIRM_COPY, SAVED_ADDRESS, CLEANER_PENDING, ADDRESS_RESULTS, PROFILE_FORM, ADDRESSES, LIVE_SESSION, LIVE_CHECKLIST,
  FAQ, NOTICES,
} from '../api/mock.js';

/**
 * api/http.js 의 ApiError 와 같은 모양 (status, code, message).
 * http.js 를 이 모듈로 바꾸면 customer.js 가 './http.js' 에서 가져가는 ApiError 도 이것이 된다.
 * DEV 의 VITE_API_MOCK 은 이 모듈의 http 만 가져가므로, http.js 의 가짜 분기가 여기서 던진 오류를 http.js 의 ApiError 로
 *   바꿔 던진다 — 그래야 customer.js 의 `instanceof ApiError` 검사(예: 로그인 LOGIN_FAILED, 기본 주소 404)가 실제 서버일 때처럼 맞는다.
 *   http.js 를 import 해 그 클래스를 쓰지 않는 것은 순환을 피하려서다. name 을 'ApiError' 로 두어야 http.js 가 알아본다.
 */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export const FAKE_USER_ID = '11111111-1111-1111-1111-111111111111'; // 가짜 서버가 로그인시키는 고객의 id (아래 user() 참고)
export const VALID_CODE = '123456'; // POST /auth/phone/verify 에서 맞는 인증번호. 다른 값이면 OTP_INVALID
/** 비밀번호 재설정: 이 비밀번호로 보내면 다른 요청이 토큰을 먼저 소비한 것처럼 OTP_EXPIRED (감사 H-01) */
export const RACED_RESET_PASSWORD = 'raced-password1';
/** 이 비밀번호로 재설정하면 서버가 비밀번호는 바꿨는데 새 세션 발급만 실패한 것처럼 PASSWORD_CHANGED (자체 점검 #22) */
export const SESSION_LOST_RESET_PASSWORD = 'session-lost1';
export const TAKEN_PHONE = '00000000'; // 이미 가입한 번호로 취급
export const UNKNOWN_PHONE = '99999999'; // 비밀번호 재설정에서 '가입되지 않은 번호'
export const WRONG_PASSWORD = 'wrong-password'; // 로그인·회원 탈퇴에서 이 비밀번호면 LOGIN_FAILED(401)
/** 이 id 로 취소·변경하면 서버가 기한 규칙(state.changeDeadlineHours, 기본 24시간)으로 거부한다 */
export const LOCKED_RESERVATION_ID = 'r-locked';
/** 녹화본이 준비된 완료 예약 (mock RESERVATIONS 의 r3) */
export const READY_BOOKING_ID = 'r3';
/** 라이브 진행 중 예약 (mock r0) */
export const LIVE_BOOKING_ID = 'r0';
// 주의: 예약 목록(mock RESERVATIONS)에서 r4 는 '취소' 탭 카드다. '끝남 + 업로드 중' 은 아래 GET …/live 응답에서만 흉내 낸다
/** 끝났는데 녹화본이 아직 업로드 중인 예약. `__fake.readyRecordings.add(id)` 로 준비 상태로 바꾼다 */
export const UPLOADING_BOOKING_ID = 'r4';
/** 임시 가격표 (backend V002 와 같은 값) */
export const PRICES = { home: { recurring: 80000, oneTime: 100000 }, office: { recurring: 120000, oneTime: 150000 }, appliance: { recurring: 60000, oneTime: 70000 }, 'move-in': { recurring: 150000, oneTime: 180000 } };

// 가짜 서버의 로그인 사용자. 기본은 가입을 마친 고객이고, patch 로 가입 단계(signupStep)·이름 등만 바꿔 만든다
const user = (patch) => ({ id: FAKE_USER_ID, role: 'customer', name: '김하늘', profileComplete: true, signupStep: null, ...patch });
// 로그인·가입·재설정 응답 모양. 토큰은 뜻 없는 고정 문자열이다
const sessionOf = (u) => ({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 3600, user: u });
const won = (n) => `${n.toLocaleString('ko-KR')}원`; // 80000 → '80,000원'
const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']; // Date.getDay() 순서 (0 = 일요일)
// 'YYYY-MM-DD' → '2026년 11월 1일 (일)' (날짜 문자열은 fromDateKey 로 읽는다 — CODE_GUIDE.md 2-2)
const dateLongWeekday = (key) => {
  const d = fromDateKey(key);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})`;
};
// 'HH:MM' → '오후 2시' / '오전 9시 30분'. 12시는 '오후 12시', 0시는 '오전 12시'
const timeKo = (time) => {
  const [h, m] = time.split(':').map(Number);
  return `${h < 12 ? '오전' : '오후'} ${h % 12 === 0 ? 12 : h % 12}시${m ? ` ${m}분` : ''}`;
};
// Date → 'YYYY-MM-DD'. @ansimclean/ui 의 toDateKey 와 같은 일을 한다 (현지 날짜 기준, toISOString 의 UTC 날짜를 쓰지 않는다)
const toKey = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
/** 서버 §6.8: 첫 방문일부터 매월 같은 날짜 1회씩 SERIES_MONTHS 회 (주말이면 다음 평일) — 사용자 결정 2026-09-22.
 *  회차 날짜는 mock 기본값으로 만든다 (state.seriesTermMonths 는 confirm-copy 응답의 안내 값만 바꾼다) */
const SERIES_MONTHS = SERIES_TERM_MONTHS;
// 정기청소 회차 날짜 목록 ('YYYY-MM-DD' 배열, 첫 방문일 포함 SERIES_MONTHS 개).
// 예: 1월 31일 시작이면 2월은 말일, 그 날이 토·일이면 다음 월요일로 민다.
const visitDates = (first) => {
  const start = fromDateKey(first);
  const out = [];
  for (let i = 0; i < SERIES_MONTHS; i += 1) {
    const d = new Date(start.getFullYear(), start.getMonth() + i + 1, 0); // 그 달의 마지막 날
    d.setDate(Math.min(start.getDate(), d.getDate())); // 31일처럼 없는 날짜는 그 달 마지막 날
    if (d.getDay() === 6) d.setDate(d.getDate() + 2); // 토 → 월
    if (d.getDay() === 0) d.setDate(d.getDate() + 1); // 일 → 월
    out.push(toKey(d));
  }
  return out;
};
// 예약 금액 계산 (POST /customer/bookings/quote 응답, 예약 생성도 같은 값을 쓴다).
// 회당 금액 = PRICES 기본가 + 고른 유료 옵션 가격, 총액 = 회당 금액 × 회차 수 (정기청소면 visitDates 개수, 1회면 1).
// 모르는 카테고리는 home 가격, frequency 가 없으면 정기청소로 계산한다. 옵션 가격은 mock 문구('+3,000원')에서 숫자만 뽑는다.
const quoteOf = (b) => {
  const category = BOOKING_CATEGORIES[b.category] ? b.category : 'home';
  const frequency = b.frequency ?? 'recurring';
  const paid = (BOOKING_OPTIONS[category]?.paid ?? []).filter((o) => (b.paidOptions ?? []).includes(o.key));
  const options = paid.reduce((sum, o) => sum + Number(o.price.replace(/[^\d]/g, '')), 0);
  const total = PRICES[category][frequency] + options;
  const dates = frequency === 'recurring' ? visitDates(b.date) : [b.date];
  return {
    perVisit: { base: PRICES[category][frequency], options, total, label: won(total) },
    visits: dates.map((date) => ({ date, label: dateLongWeekday(date) })),
    visitsPlanned: dates.length,
    totalAmount: total * dates.length,
    totalLabel: won(total * dates.length),
    paymentNote: '결제는 청소 완료 후 진행돼요',
  };
};

/**
 * http.js 대신 쓸 모듈을 만든다: { ApiError, newIdempotencyKey, http, __fake }.
 * 부를 때마다 새 state 를 만든다. 예약·주소·프로필은 mock 값을 복사해 담으므로 테스트가 바꿔도 mock.js 원본은 그대로다.
 */
export function fakeHttpModule() {
  // 가짜 서버의 DB 역할. 테스트에는 __fake 라는 이름으로 그대로 보인다
  const state = {
    user: user(), // 지금 로그인한 사용자. 가입 단계 요청·비밀번호 재설정이 바꾼다
    calls: [], // 받은 요청 기록 { method, path, body, …opts(idempotencyKey 등) } — 테스트가 무엇을 보냈는지 확인한다
    inquiries: [], // 1:1 문의 내용(문자열). 목록에서는 첫 번째 것만 '답변 완료'로 보여준다
    pushTokens: [], // PUT /push/token 으로 받은 본문들. DELETE /push/token 은 token 이 같은 것을 뺀다
    liveStates: {}, // 예약 id → 라이브 state. 넣어 두면 GET …/live 가 기본값 대신 이 값을 준다
    liveChecklists: {}, // 예약별 체크리스트. 테스트에서 폴링으로 바뀌는 서버 응답을 꾸민다.
    failOnce: new Set(), // 'GET /경로' 모양의 문자열. 넣은 요청은 한 번만 NETWORK 로 실패한다 (아래 call 참고)
    holdOnce: new Map(), // 'GET /경로' → Promise. 그 요청의 응답(요청 때 만든 값)을 Promise 가 끝날 때까지 한 번 늦춘다 — 늦게 도착한 옛 응답 흉내
    // 서버 설정 값 흉내: 변경·취소 기한(cancel_deadline_hours)·정기청소 회차 수(series_term_months). 테스트가 바꿔 운영자가 설정을 바꾼 경우를 만든다
    changeDeadlineHours: CHANGE_DEADLINE_HOURS, // 홈·내 예약·시간표 응답의 changeDeadlineHours, 기한 거절 문구의 시간
    seriesTermMonths: SERIES_TERM_MONTHS, // confirm-copy 응답의 seriesTermMonths
    /** 내 예약 카드 (서버 모양). 만든 예약은 앞에 붙는다 */
    reservations: RESERVATIONS.map((r) => ({ ...r, series: false, recordingReady: r.id === READY_BOOKING_ID, changeable: true, rows: r.rows.map((row) => ({ ...row })) })),
    bookings: {}, // 만든 예약. 멱등 키(없으면 예약 id)와 예약 id 로 같은 객체를 넣는다
    profile: { ...PROFILE_FORM }, // GET·PATCH /customer/profile 이 읽고 바꾸는 값
    addresses: ADDRESSES.map((a) => ({ ...a })), // /customer/addresses… 경로가 읽고 바꾸는 주소 목록 (예: 추가·삭제·기본 변경)
    hasDefaultAddress: true, // false 면 GET /customer/addresses/default 가 404. true 면 addresses 와 상관없이 mock SAVED_ADDRESS 를 준다
    /** 녹화본이 준비된 예약 id (READY_BOOKING_ID 외). 테스트가 넣는다 */
    readyRecordings: new Set(),
    /** 서버 정리(24시간)로 이미 지워진 수리 사진 path. 테스트가 넣으면 그 사진을 붙인 예약 생성이 만료 오류(VALIDATION)로 막힌다 */
    expiredPhotos: new Set(),
  };
  const notFound = (what) => new ApiError(404, 'NOT_FOUND', `찾을 수 없어요: ${what}`); // 던질 오류를 만들기만 한다
  // 내 예약 카드 하나 (state 안의 그 객체라 바꾸면 목록에도 반영된다). 없으면 404
  const reservationOf = (id) => {
    const r = state.reservations.find((x) => x.id === id);
    if (!r) throw notFound(id);
    return r;
  };
  // 카드를 '취소' 탭으로 옮긴다. 이 가짜 서버로 만든 예약이면 상세(GET /customer/bookings/{id})도 서버처럼 cancelled 와
  // 매니저 없이 취소된 매니저 칸 '배정 전 취소'(BookingMapper.managerLabel)로 바꾼다
  const cancelCard = (r) => {
    const booking = state.bookings[r.id];
    if (booking) Object.assign(booking, { cancelled: true, ...(booking.managerAssigned ? {} : { cleaner: '배정 전 취소' }) });
    return Object.assign(r, { tab: 'cancelled', status: 'cancelled' });
  };

  // 메서드 + 경로(쿼리 포함)가 글자 그대로 같을 때 쓰는 처리 함수들.
  // 각 함수는 (본문, opts) 를 받아 응답을 돌려주거나 ApiError 를 던진다 (본문이 없으면 {}).
  const exact = {
    'GET /terms?audience=customer': () => TERMS,
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
    'POST /auth/password/reset': (b) => {
      // 'tok' 은 POST /auth/phone/verify 가 주는 인증 토큰. 다른 값이면 인증이 만료된 것으로 본다 (가입도 같다)
      if (b.verificationToken !== 'tok' || b.password === RACED_RESET_PASSWORD) throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 다시 해주세요');
      if (b.password === SESSION_LOST_RESET_PASSWORD) throw new ApiError(409, 'PASSWORD_CHANGED', '비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요');
      state.user = user({ profileComplete: true });
      return sessionOf(state.user);
    },
    'POST /auth/phone/verify': (b) => {
      if (b.code !== VALID_CODE) throw new ApiError(400, 'OTP_INVALID', '인증번호가 맞지 않아요');
      return { verificationToken: 'tok', expiresIn: 900 };
    },
    'POST /auth/signup': (b) => {
      if (b.verificationToken !== 'tok') throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 다시 해주세요');
      // 가입 단계: 가입 직후 'address' → 주소 저장 'housing' → 주거 형태 저장 'complete' → 가입 완료 null (아래 핸들러들)
      state.user = user({ name: b.name, profileComplete: false, signupStep: 'address' });
      return sessionOf(state.user);
    },
    'PUT /customer/profile/address': (b) => {
      state.user = { ...state.user, signupStep: 'housing' };
      return { id: 'addr', label: '집', ...b };
    },
    'PUT /customer/profile/housing': (b) => {
      state.user = { ...state.user, signupStep: 'complete' };
      return b;
    },
    'POST /auth/signup/complete': (b) => {
      // 필수 약관(TERMS 중 required) id 가 모두 agreedTermIds 에 있어야 가입을 마친다
      const required = TERMS.filter((t) => t.required).map((t) => t.id);
      if (!required.every((id) => (b.agreedTermIds ?? []).includes(id))) throw new ApiError(400, 'VALIDATION', '필수 약관에 동의해주세요');
      state.user = { ...state.user, profileComplete: true, signupStep: null };
      return { profileComplete: true, user: state.user };
    },
    'GET /customer/profile/suggested-notices': () => ({ notices: ['pet'] }),
    'GET /auth/me': () => state.user,
    'POST /auth/logout': () => ({ ok: true }),
    // 수리 사진 업로드 흉내: 파일은 저장하지 않고 저장 경로와 가짜 미리보기 url 만 돌려준다
    'POST /customer/repair-photos': () => ({ path: `repair/${state.pushTokens.length}-${Date.now()}.jpg`, url: 'blob:fake-photo' }),
    'GET /support/faq?audience=customer': () => FAQ,
    'GET /support/notices?audience=customer': () => NOTICES.map(({ id, title, date }) => ({ id, title, date })), // 목록에는 본문(body)을 빼고 준다
    // 내 문의 목록: 첫 번째 문의만 답변 완료(answered), 나머지는 대기(open). 날짜 문구는 고정값
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
      // 실서버(AuthController)는 { ok: true } 를 주지만, customer.js deleteAccount 는 응답 본문을 쓰지 않는다
      return null;
    },
    // ── B3 고객 ──
    // upcoming = '예정' 탭 카드 중 날짜·시각('YYYY-MM-DD HH:MM' 문자열 비교)이 가장 이른 것, 없으면 null
    'GET /customer/home': () => ({ slides: HOME_SLIDES, upcoming: state.reservations.filter((r) => r.tab === 'upcoming').sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`))[0] ?? null, changeDeadlineHours: state.changeDeadlineHours }),
    'GET /customer/repair': () => ({ hero: REPAIR_HERO }),
    'GET /customer/reservations': () => ({ header: RESERVATIONS_HEADER, tabs: RESERVATION_TABS, reservations: state.reservations, changeDeadlineHours: state.changeDeadlineHours }),
    'GET /customer/settings': () => ({ header: SETTINGS_HEADER, profile: PROFILE, groups: SETTINGS_GROUPS, notifications: { ...NOTIFICATION_SETTINGS } }),
    'GET /catalog/time-slots': () => ({ ...TIME_SLOTS, unavailable: [], bookableDays: BOOKABLE_DAYS, changeDeadlineHours: state.changeDeadlineHours }), // 막힌 시간(unavailable)은 없다고 답한다
    'GET /customer/addresses/default': () => {
      if (!state.hasDefaultAddress) throw notFound('default address');
      return SAVED_ADDRESS;
    },
    'POST /customer/bookings/quote': (b) => quoteOf(b),
    // 예약 생성: 멱등 검사 → 무료 옵션 개수·카테고리 검사 → 금액 계산 → 예약 저장 → 회차마다 '내 예약' 카드 추가
    'POST /customer/bookings': (b, { idempotencyKey }) => {
      const saved = idempotencyKey && state.bookings[idempotencyKey];
      if (saved) {
        // 서버 §6.9: 같은 키 + 같은 본문 = 저장된 응답, 본문이 다르면 422
        if (JSON.stringify(saved.__request) !== JSON.stringify(b)) throw new ApiError(422, 'IDEMPOTENCY_MISMATCH', '이전 요청과 내용이 달라요');
        return saved;
      }
      if ((b.freeOptions ?? []).length > FREE_OPTION_LIMIT) throw new ApiError(400, 'VALIDATION', `무료 옵션은 ${FREE_OPTION_LIMIT}개까지 고를 수 있어요`);
      const repair = b.category === 'repair'; // 수리: 금액 0, 라벨 '수리 서비스' (서버 seed 와 같다)
      // 서버 RepairPhotoService.attach: 붙이려는 사진이 정리로 이미 지워졌으면 예약을 만들지 않는다
      if (repair && (b.repair?.photos ?? []).some((p) => state.expiredPhotos.has(p))) throw new ApiError(400, 'VALIDATION', '첨부한 사진이 만료됐어요. 사진을 다시 올려주세요');
      if (!repair && !BOOKING_CATEGORIES[b.category]) throw notFound(b.category);
      const q = repair ? { visits: [{ date: b.date, label: dateLongWeekday(b.date) }], perVisit: { label: '0원' }, totalAmount: 0, totalLabel: '0원' } : quoteOf(b);
      // 예약 id 는 bookings 키 개수로 매긴다. 멱등 키가 있으면 한 예약이 키를 두 개 차지해 번호가 건너뛸 수 있다
      const id = `booking-${Object.keys(state.bookings).length + 1}`;
      const seriesId = b.frequency === 'recurring' ? `${id}-series` : null; // 정기청소 회차들을 묶는 id — 전체 취소(wholeSeries)가 이것으로 찾는다
      const booking = {
        id, category: b.category, frequency: b.frequency, date: b.date, time: b.time, roadAddress: b.roadAddress, detailAddress: b.detailAddress,
        serviceLabel: repair ? '수리 서비스' : BOOKING_CATEGORIES[b.category].label, cleaner: CLEANER_PENDING, managerAssigned: false, matchingFailed: false, cancelled: false, seriesId, repair: b.repair ?? null, repairType: b.repairType ?? null,
        visits: q.visits.map((v, i) => ({ id: i === 0 ? id : `${id}-${i}`, ...v })),
        perVisitLabel: q.perVisit.label, totalAmount: q.totalAmount, totalLabel: q.totalLabel,
        // 테스트가 들여다보는 원본 요청
        __request: b,
      };
      state.bookings[idempotencyKey ?? id] = booking; // 멱등 키가 있으면: 같은 키로 다시 오면 위에서 이 응답을 그대로 돌려준다
      state.bookings[id] = booking; // GET /customer/bookings/{id} 가 찾는 이름
      // 회차마다 '내 예약' 카드를 만들어 목록 맨 앞에 붙인다. 서버도 정기청소 회차 전부를 생성 즉시 만든다 (domain.md §6.8)
      const cards = booking.visits.map((v) => ({
        id: v.id, tab: 'upcoming', category: b.category, title: booking.serviceLabel, status: 'assigning', date: v.date, time: b.time, series: Boolean(seriesId), seriesId, recordingReady: false, changeable: true,
        rows: [{ label: '일정', value: `${dateLongWeekday(v.date)} ${timeKo(b.time)}` }, { label: '주소', value: b.roadAddress }, { label: '매니저', value: CLEANER_PENDING }],
      }));
      state.reservations = [...cards, ...state.reservations];
      return booking;
    },
    'GET /customer/profile': () => ({ ...state.profile }),
    // name·nickname 만 바꾸고 프로필 사본을 돌려준다 (Object.assign 은 바꾼 객체를 돌려줘 늘 참이라 && 뒤가 응답이 된다)
    'PATCH /customer/profile': (b) => Object.assign(state.profile, { name: b.name, nickname: b.nickname }) && { ...state.profile },
    'GET /customer/addresses': () => state.addresses.map((a) => ({ ...a })),
    'POST /customer/addresses': (b) => {
      // 이름(label)이 비면 '기타'. 목록이 비어 있을 때 넣은 첫 주소만 기본 주소가 된다
      const item = { id: `addr-${state.addresses.length + 1}`, label: b.label || '기타', roadAddress: b.roadAddress, detailAddress: b.detailAddress, isDefault: state.addresses.length === 0 };
      state.addresses.push(item);
      return item;
    },
    'GET /customer/payment-methods': () => [], // 결제 수단 없음 (실서버도 PG 연동 전에는 [] — api.md)
  };
  // 경로에 id·검색어가 들어가는 요청: [메서드, 경로 정규식, 처리 함수]. 위에서부터 처음 맞는 것을 쓴다.
  // 처리 함수는 (정규식 괄호로 잡은 값 배열, 본문, opts) 를 받는다. 예: '/terms/privacy?audience=customer' → ['privacy']
  const patterns = [
    ['GET', /^\/support\/notices\/([^?]+)\?audience=customer$/, ([id]) => {
      const n = NOTICES.find((x) => x.id === id);
      if (!n) throw new ApiError(404, 'NOT_FOUND', '찾을 수 없어요');
      return { ...n };
    }],
    ['GET', /^\/terms\/([^?]+)\?audience=customer$/, ([id]) => {
      const t = TERM_BODIES[id];
      if (!t) throw notFound(id);
      return { id, ...t };
    }],
    // ?? 뒤의 (() => { throw … })() 는 식 한가운데서 오류를 던지려고 바로 부르는 함수다 — 없는 카테고리면 404
    ['GET', /^\/catalog\/categories\/([^/]+)$/, ([c]) => BOOKING_CATEGORIES[c] ?? (() => { throw notFound(c); })()],
    // 옵션이 없는 카테고리는 404 대신 빈 목록
    ['GET', /^\/catalog\/categories\/([^/]+)\/options$/, ([c]) => ({ ...(BOOKING_OPTIONS[c] ?? { free: [], paid: [] }), freeLimit: FREE_OPTION_LIMIT })],
    ['GET', /^\/catalog\/categories\/([^/]+)\/notice-chips$/, ([c]) => ({ chips: NOTICE_CHIPS[c] ?? [], copy: NOTES_COPY })],
    ['GET', /^\/catalog\/categories\/([^/]+)\/confirm-copy$/, ([c]) => ({ copy: CONFIRM_COPY, frequencyDescriptions: FREQUENCY_DESCRIPTIONS[c] ?? FREQUENCY_DESCRIPTIONS.home, seriesTermMonths: state.seriesTermMonths })],
    ['GET', /^\/catalog\/address\?q=(.*)$/, ([q]) => ({ query: decodeURIComponent(q), results: ADDRESS_RESULTS })], // 검색어와 상관없이 같은 결과
    ['GET', /^\/customer\/bookings\/([^/]+)\/checklist$/, ([id]) => structuredClone(state.liveChecklists[id] ?? LIVE_CHECKLIST)],
    // 라이브 세션 상태. 기본값: 녹화본 준비(READY·readyRecordings)나 업로드 중(UPLOADING)이면 ended,
    // LIVE_BOOKING_ID 는 live, 나머지는 waiting. recording 은 'ready'|'pending'|'none' 만 흉내 낸다 ('failed' 는 없다)
    ['GET', /^\/customer\/bookings\/([^/]+)\/live$/, ([id]) => {
      const ready = id === READY_BOOKING_ID || state.readyRecordings.has(id);
      const state0 = state.liveStates[id] ?? (ready || id === UPLOADING_BOOKING_ID ? 'ended' : id === LIVE_BOOKING_ID ? 'live' : 'waiting'); // __fake.liveStates[id] = 'paused' 등으로 서버 상태를 바꾼다
      return { ...LIVE_SESSION, live: { state: state0, available: state0 === 'live', recordingReady: ready, recordingPending: !ready && id === UPLOADING_BOOKING_ID, recording: ready ? 'ready' : id === UPLOADING_BOOKING_ID ? 'pending' : 'none' } };
    }],
    // 시청 토큰: LIVE_BOOKING_ID 만 받을 수 있다. url·token 은 가짜 값이다.
    // liveStates 는 보지 않는다 — 실서버는 paused 등 영상 중단 중이면 LIVE_NOT_AVAILABLE 로 거부한다 (api.md)
    ['POST', /^\/live\/sessions\/([^/]+)\/token$/, ([id]) => {
      if (id !== LIVE_BOOKING_ID) throw new ApiError(409, 'LIVE_NOT_AVAILABLE', '지금은 실시간 영상을 볼 수 없어요');
      return { url: 'ws://fake', token: 'viewer-token', roomName: `booking-${id}` };
    }],
    // 녹화본 주소: 준비된 예약만, 아니면 RECORDING_NOT_READY(404). 모든 예약이 같은 예시 주소를 받는다
    ['GET', /^\/live\/sessions\/([^/]+)\/recording$/, ([id]) => {
      if (id !== READY_BOOKING_ID && !state.readyRecordings.has(id)) throw new ApiError(404, 'RECORDING_NOT_READY', '녹화 영상이 아직 준비되지 않았어요');
      return { url: 'https://example.com/recordings/r3.mp4', durationSeconds: 630 };
    }],
    ['GET', /^\/customer\/bookings\/([^/]+)$/, ([id]) => state.bookings[id] ?? (() => { throw notFound(id); })()], // 이 가짜 서버로 만든 예약만 찾는다
    // 일정 변경: 카드의 date·time 과 첫 줄('일정', rows[0]) 문구를 바꾼다
    ['PATCH', /^\/customer\/reservations\/([^/]+)\/schedule$/, ([id], b) => {
      if (id === LOCKED_RESERVATION_ID) throw new ApiError(409, 'BOOKING_NOT_CHANGEABLE', `방문 ${state.changeDeadlineHours}시간 전까지만 변경할 수 있어요`); // 서버 Booking.reschedule 과 같은 문구
      const r = reservationOf(id);
      Object.assign(r, { date: b.date, time: b.time });
      r.rows[0].value = `${dateLongWeekday(b.date)} ${timeKo(b.time)}`;
      return r;
    }],
    // 취소: wholeSeries 면 같은 시리즈의 '예정' 카드 전부, 아니면 그 카드 하나.
    // 기한 안의 회차를 남기는 서버 규칙(domain.md §6.8)은 흉내 내지 않는다 — 예정 카드면 전부 취소된다
    ['POST', /^\/customer\/reservations\/([^/]+)\/cancel$/, ([id], b) => {
      if (id === LOCKED_RESERVATION_ID) throw new ApiError(409, 'BOOKING_NOT_CANCELLABLE', `방문 ${state.changeDeadlineHours}시간 전까지만 취소할 수 있어요`); // 서버 Booking.cancel 과 같은 문구
      const r = reservationOf(id);
      if (b?.wholeSeries && r.seriesId) state.reservations.filter((x) => x.seriesId === r.seriesId && x.tab === 'upcoming').forEach(cancelCard);
      else cancelCard(r);
      return r;
    }],
    ['PUT', /^\/customer\/notifications\/([^/]+)$/, ([key], b) => ({ key, enabled: b.enabled })], // 받은 값을 돌려주기만 하고 저장하지 않는다
    ['DELETE', /^\/customer\/addresses\/([^/]+)$/, ([id]) => {
      const wasDefault = state.addresses.find((a) => a.id === id)?.isDefault;
      state.addresses = state.addresses.filter((a) => a.id !== id);
      if (wasDefault && state.addresses[0]) state.addresses[0].isDefault = true; // 서버처럼 다음 주소가 기본
      return { id };
    }],
    // 기본 주소 바꾸기: 고른 주소만 isDefault=true 로 하고 전체 목록을 돌려준다
    ['PUT', /^\/customer\/addresses\/([^/]+)\/default$/, ([id]) => {
      if (!state.addresses.some((a) => a.id === id)) throw notFound(id);
      state.addresses.forEach((a) => (a.isDefault = a.id === id));
      return state.addresses.map((a) => ({ ...a }));
    }],
  ];
  // 모든 요청이 지나가는 곳: 기록 → (failOnce 면 한 번 실패) → route(exact → patterns, 없으면 404) → (holdOnce 면 도착을 늦춤).
  // async 함수라 처리 함수가 throw 해도 진짜 http.js 처럼 거부된(rejected) Promise 로 돌아간다.
  const call = async (method, path, body, opts = {}) => {
    state.calls.push({ method, path, body, ...opts });
    if (state.failOnce.has(`${method} ${path}`)) { // __fake.failOnce.add('GET /…') → 그 요청만 한 번 NETWORK 로 실패
      state.failOnce.delete(`${method} ${path}`);
      throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요');
    }
    const result = await route(method, path, body, opts);
    const gate = state.holdOnce.get(`${method} ${path}`);
    if (gate) { // 응답 값은 지금(요청 때) 정해졌고, 도착만 늦춘다
      state.holdOnce.delete(`${method} ${path}`);
      await gate;
    }
    return result;
  };
  // exact(경로 전체가 같은 것) → patterns(정규식) 순으로 처리 함수를 찾아 응답을 만든다. 없으면 404
  const route = async (method, path, body, opts) => {
    const handler = exact[`${method} ${path}`];
    if (handler) return handler(body ?? {}, opts);
    for (const [m, re, fn] of patterns) {
      const match = m === method && path.match(re);
      if (match) return fn(match.slice(1), body ?? {}, opts); // slice(1): 괄호로 잡은 값들만 (0번은 경로 전체)
    }
    throw new ApiError(404, 'NOT_FOUND', `가짜 서버에 없는 경로: ${method} ${path}`);
  };
  // http.js 가 내보내는 이름(ApiError·newIdempotencyKey·http) + 테스트용 __fake
  return {
    ApiError,
    // 테스트에서 이 함수로 만든 멱등 키는 늘 'key-1' 이다 (예: BookingConfirm.test 가 확인한다). 그래서 한 state 에서 이 키로
    // 내용이 다른 예약을 또 만들면 POST /customer/bookings 의 멱등 검사에 걸려 422 가 난다 (키를 직접 넘기는 테스트는 예외).
    // DEV 의 VITE_API_MOCK 은 이 모듈의 http 만 쓰므로 키는 http.js 의 진짜 무작위 키다
    newIdempotencyKey: () => 'key-1',
    // http.js 의 http 와 같은 함수 모양. get 은 본문을 받지 않는다
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
