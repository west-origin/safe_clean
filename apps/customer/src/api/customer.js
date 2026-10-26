// 고객용 앱 데이터 어댑터. 화면은 여기 함수만 부른다. (스키마: api/README.md)
// 서버(backend)를 부르는 함수는 http.js 를 거치고, 서버 오류는 ApiError 로 던진다 (화면의 useAsync/useAction 이 받는다).
// Figma 문구처럼 앱 안에 둔 값(mock.js)을 그대로 돌려주는 함수, 가입 초안(localStorage)·세션(session.js)을 다루는 함수도 여기 있다.
// 화면 말고도 부르는 곳이 있다 (예: App.jsx 의 refreshMe, live/liveClient.js 의 라이브 세션·시청 토큰). 전부 찾으려면 검색한다.
import { SIGN_UP_METHODS, PASSWORD_OK_MESSAGE, HOUSING_TYPES, PET_OPTIONS, FREQUENCY_OPTIONS, ADDRESS_REUSE_OPTIONS, COMPLETE_ROWS, LIVE_SESSION, LIVE_CHECKLIST, REPAIR_BOOKING } from './mock.js';
import { deviceToken } from '../push/deviceToken.js';

import { http, ApiError, newIdempotencyKey } from './http.js';
import { session } from './session.js';

/** 가입까지 끝난 로그인 상태 (RequireAuth). 토큰만 있고 가입이 덜 끝났으면 false → signupStep() 으로 이어간다 */
export const isSignedIn = () => session.isSignedIn();
/** 세션 변화 구독 (RequireAuth) */
export const subscribeSession = session.subscribe;
/** 가입 도중 앱을 닫았다 돌아온 사용자의 다음 화면 ('address' | 'housing' | 'complete' | null) */
export const signupStep = () => session.signupStep();
/** signupStep → 화면 경로 */
export const SIGNUP_STEP_PATHS = { address: '/auth/profile-address', housing: '/auth/profile-housing', complete: '/auth/complete' };

/** 가입 초안: 약관 체크·휴대폰 인증 토큰을 가입이 끝날 때까지 들고 있는다 (BACKEND_PLAN.md §5.1).
 *  localStorage — 가입 도중 앱을 닫았다 열거나 새 탭에서 이어가도 약관 동의가 남는다(sessionStorage 면 사라져 완료 요청이 빈 배열로 거부된다) */
const SIGNUP_DRAFT_KEY = 'ansimclean.signup';
let signupDraftMemory = null; // 저장소가 막힌 환경의 사본 — 인증 토큰이 저장되지 않아 '인증을 먼저 하라' 가 반복되지 않게 (session.js 와 같은 규칙, 검수 3차)
// 가입 초안을 읽는다 → { agreedTermIds, verificationToken, phone } 중 저장된 것 (없으면 {})
const readSignupDraft = () => {
  try {
    return JSON.parse(localStorage.getItem(SIGNUP_DRAFT_KEY)) ?? signupDraftMemory ?? {}; // 저장소가 진실, 메모리는 저장소가 비었거나 막혔을 때만
  } catch {
    return signupDraftMemory ?? {};
  }
};
// patch 의 칸만 기존 초안에 덮어써서 저장한다 (메모리 사본도 같이 바꾼다)
const updateSignupDraft = (patch) => {
  const next = { ...readSignupDraft(), ...patch };
  signupDraftMemory = next;
  try {
    localStorage.setItem(SIGNUP_DRAFT_KEY, JSON.stringify(next));
  } catch {
    // 저장소를 쓸 수 없는 환경 — 메모리 사본으로 이번 실행 동안만 진행된다
  }
};
// 초안을 통째로 지운다 — 가입이 끝나거나 이 기기의 계정이 바뀔 때(예: 로그인·로그아웃) 부른다. 앞 사람의 약관 동의·인증 토큰으로 가입을 끝내지 않게
const clearSignupDraft = () => {
  signupDraftMemory = null;
  try {
    localStorage.removeItem(SIGNUP_DRAFT_KEY);
  } catch {
    // 저장소를 쓸 수 없는 환경 — 로그아웃·탈퇴는 그래도 진행된다
  }
};

/** DEV 에서 ?scenario=empty 면 빈 상태, ?scenario=error 면 실패(ScreenError 확인용)를 돌려준다 */
export const isEmptyScenario = () =>
  import.meta.env.DEV && new URLSearchParams(window.location.search).get('scenario') === 'empty';
const isErrorScenario = () => import.meta.env.DEV && new URLSearchParams(window.location.search).get('scenario') === 'error';
// DEV 에서 ?scenario=error 면 던진다 — 서버를 부르기 전에 부른다 (예: getHome). 화면의 ScreenError 를 확인하는 용도
const failIfErrorScenario = () => {
  if (isErrorScenario()) throw new Error('DEV ?scenario=error');
};

/** 가입 방식 선택. 소셜 로그인은 결정(§13-1)대로 아직 없음 — 어느 버튼이든 휴대폰 가입 흐름으로 간다 */
export async function signUpWith(method) {
  if (!SIGN_UP_METHODS.includes(method)) throw new Error(`signUpWith: 알 수 없는 method "${method}"`);
  return { method: 'phone' };
}

/** Auth/TermsConsent — 약관 목록 [{ id, label, required }] */
export async function getTerms() {
  return http.get('/terms?audience=customer');
}

/** Auth/TermsConsent — 체크한 약관 id 를 초안에 둔다. 서버에는 가입 완료(completeSignUp) 때 보낸다 */
export async function agreeTerms(agreedIds) {
  updateSignupDraft({ agreedTermIds: agreedIds });
  return { agreedIds };
}
/** 초안에 남은 약관 체크 (약관 본문을 보고 돌아왔을 때 복원) */
export function getAgreedTermsDraft() {
  return readSignupDraft().agreedTermIds ?? [];
}

/** Auth/LoginPhone — 전화번호(앞자리 010 제외) + 비밀번호. 틀리면 { ok: false } (문구는 화면이 고른다), 그 외 오류는 던진다 */
export async function loginWithPhone({ phone, password }) {
  try {
    const res = await http.post('/auth/login', { phone, password, role: 'customer' });
    clearSignupDraft(); // 다른 사람이 남긴 약관 동의·인증 토큰으로 가입을 끝내지 않게
    session.set(res);
    return { ok: true, user: res.user };
  } catch (e) {
    if (e instanceof ApiError && e.code === 'LOGIN_FAILED') return { ok: false };
    throw e;
  }
}

/** Auth/ProfileInfo-* › 비밀번호 검사. 8자 이상이면 사용 가능 (서버도 같은 규칙으로 다시 검사한다) */
export async function checkPassword(password) {
  return { ok: password.length >= 8, message: password.length >= 8 ? PASSWORD_OK_MESSAGE : '' };
}

/** Auth/ProfileInfo-* › "인증하기" — 인증번호 문자 발송. 이미 가입한 번호면 { sent: false, reason: 'taken' } (화면이 로그인 안내) */
export async function requestPhoneVerification(phone) {
  try {
    const res = await http.post('/auth/phone/otp', { phone, role: 'customer' });
    return { phone, sent: res.sent, expiresIn: res.expiresIn, resendAfter: res.resendAfter ?? 60 };
  } catch (e) {
    if (e instanceof ApiError && e.code === 'PHONE_TAKEN') return { phone, sent: false, reason: 'taken', message: e.message };
    throw e;
  }
}

/** Auth/ProfileInfo-* › 인증번호 확인. 성공하면 가입에 쓸 토큰을 초안에 둔다. 틀리면 ApiError(OTP_INVALID | OTP_EXPIRED | OTP_RATE_LIMITED) */
export async function verifyPhone(phone, code) {
  const res = await http.post('/auth/phone/verify', { phone, code, role: 'customer' });
  updateSignupDraft({ verificationToken: res.verificationToken, phone });
  return { ok: true };
}

/** Auth/ResetPassword (코드에서 정함) › "인증번호 받기" — 가입된 번호에만 보낸다. 없는 번호면 { sent: false, reason: 'notFound' } */
export async function requestPasswordReset(phone) {
  try {
    const res = await http.post('/auth/password/otp', { phone, role: 'customer' });
    return { phone, sent: res.sent, resendAfter: res.resendAfter ?? 60 };
  } catch (e) {
    if (e instanceof ApiError && e.code === 'PHONE_NOT_FOUND') return { phone, sent: false, reason: 'notFound', message: e.message };
    throw e;
  }
}

/** Auth/ResetPassword › 인증번호 확인. 성공하면 재설정에 쓸 토큰을 돌려준다(초안에 저장하지 않는다 — 가입 토큰과 섞이지 않게) */
export async function verifyPasswordReset(phone, code) {
  const res = await http.post('/auth/phone/verify', { phone, code, role: 'customer' });
  return { verificationToken: res.verificationToken };
}

/** Auth/ResetPassword › "비밀번호 변경하기" — 바꾸고 바로 로그인 상태가 된다 */
export async function resetPassword({ verificationToken, password }) {
  const res = await http.post('/auth/password/reset', { verificationToken, password });
  clearSignupDraft(); // 공용 기기에서 다른 사람이 남긴 약관 동의로 가입을 끝내지 않게 — 로그인과 같다 (자체 점검 #39·#47)
  session.set(res);
  return res.user;
}

/** Auth/ProfileInfo-* › "다음" — 계정을 만든다(휴대폰 인증 토큰 + 이름·닉네임·비밀번호). 성공하면 로그인 상태(가입 중) */
export async function saveProfileInfo({ name, nickname, password }) {
  const { verificationToken } = readSignupDraft();
  // 초안에 토큰이 없으면 서버의 '인증 만료' 와 같은 코드로 던진다 — ProfileInfo 화면이 OTP_EXPIRED 를 보고 재인증 칸을 다시 연다
  if (!verificationToken) throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 먼저 해주세요');
  const res = await http.post('/auth/signup', { verificationToken, name, nickname, password });
  session.set(res);
  updateSignupDraft({ verificationToken: null }); // 가입에 쓴 인증 토큰은 초안에서 지운다 (약관 체크·전화번호는 남는다)
  return res.user;
}

/** Auth/ProfileAddress › "다음" — 기본 주소 저장 */
export async function saveProfileAddress({ roadAddress, detailAddress }) {
  const res = await http.put('/customer/profile/address', { roadAddress, detailAddress });
  advanceSignupStep('address', 'housing'); // RequireAuth 가 옛 단계로 되돌리지 않게
  return res;
}

/** 가입 단계가 끝나면 세션의 signupStep 을 다음 단계로 (서버 응답에는 user 가 없다) */
function advanceSignupStep(from, to) {
  const user = session.user();
  if (user && user.signupStep === from) session.setUser({ ...user, signupStep: to });
}

/** Auth/ProfileHousing — 거주형태 4종 · 반려동물 2종 (Figma 문구 그대로. 아이콘이 앱 에셋이라 로컬) */
export async function getHousingOptions() {
  return { housingTypes: HOUSING_TYPES, petOptions: PET_OPTIONS };
}

/** Auth/ProfileHousing › "다음" */
export async function saveProfileHousing({ housingType, size, sizeUnknown, rooms, bathrooms, pet }) {
  const res = await http.put('/customer/profile/housing', { housingType, size, sizeUnknown: Boolean(sizeUnknown), rooms, bathrooms, pet });
  advanceSignupStep('housing', 'complete');
  return res;
}

/** Auth/SignUpComplete › "지금 시작하기" — 약관 동의를 보내고 가입을 끝낸다 */
export async function completeSignUp() {
  const { agreedTermIds = [] } = readSignupDraft();
  // 다른 기기에서 이어가는 등 동의 기록이 없으면 서버가 '필수 약관' 으로 거부한다 — 서버에 보내기 전에 약관 화면으로 돌려보낸다
  // status 0 = HTTP 응답 없이 앱이 만든 오류 (서버에 보내지 않았다).
  // SignUpComplete 화면이 TERMS_REQUIRED 를 보고 약관 화면(/auth/terms?next=complete)으로 보낸다
  if (agreedTermIds.length === 0) throw new ApiError(0, 'TERMS_REQUIRED', '약관 동의가 필요해요');
  const res = await http.post('/auth/signup/complete', { agreedTermIds });
  session.setUser(res.user);
  clearSignupDraft();
  return { ok: true, user: res.user };
}

/** 앱 시작 시 한 번: 서버가 보는 사용자 상태로 세션을 맞춘다. 토큰이 없으면 아무것도 안 한다. 401 이면 http 가 세션을 지운다 */
export async function refreshMe() {
  if (!session.accessToken()) return null;
  const userBefore = session.user()?.id ?? null;
  const user = await http.get('/auth/me');
  // 기다리는 사이 로그아웃됐거나(토큰 없음) 다른 계정으로 로그인했으면(user id 가 다름) 옛 응답으로 새 세션을 덮지 않는다 (검수 3차)
  if (!session.accessToken()) return null;
  const userNow = session.user()?.id ?? null;
  if (userNow && user?.id && userNow !== user.id) return null;
  if (userBefore && user?.id && userBefore !== user.id && userNow === userBefore) return null; // 옛 계정의 늦은 응답
  session.setUser(user);
  return user;
}

/** 설정 › 회원 탈퇴 (비밀번호 확인). 진행 중 예약이 있으면 ApiError(INVALID_STATE) */
export async function deleteAccount(password) {
  await http.delete('/auth/account', { password });
  clearSignupDraft();
  session.clear();
  return { ok: true };
}

/** Home/Realtime · Home/Standard-A — { slides(히어로 문구·이미지), upcoming(다가오는 방문 카드 | null), changeDeadlineHours(변경·취소 기한 시간) } */
export async function getHome() {
  failIfErrorScenario();
  return http.get('/customer/home');
}

/** Repair — 히어로 문구·이미지 */
export async function getRepair() {
  return http.get('/customer/repair');
}

/**
 * Reservations — 헤더 문구 · 탭 · 예약 목록 · changeDeadlineHours(변경·취소 기한 시간 — 잠긴 카드·취소 시트 안내).
 * tab/status/rows/series/changeable 은 서버가 만든다. ?scenario=empty 면 빈 목록
 */
export async function getReservations() {
  failIfErrorScenario();
  const res = await http.get('/customer/reservations');
  return isEmptyScenario() ? { ...res, reservations: [] } : res;
}

/** Reservations › "예약 변경" — 날짜·시간을 바꾼다. 일정 행 문구는 서버가 만든다. 기한(방문 N시간 전)이 지났으면 ApiError(BOOKING_NOT_CHANGEABLE) */
export async function changeReservation(id, { date, time }) {
  // 부르는 곳은 BookingDateTime 화면(?edit=예약 id) — 내 예약 카드의 '예약 변경' 이 그 화면으로 보낸다
  return http.patch(`/customer/reservations/${encodeURIComponent(id)}/schedule`, { date, time });
}

/**
 * Reservations › "예약 취소". 정기청소는 wholeSeries=true 로 남은 회차까지 취소 (기한 안에 시작하는 회차는 남는다).
 * 기한(방문 N시간 전, N = 서버 cancel_deadline_hours)이 지났으면 ApiError(BOOKING_NOT_CANCELLABLE)
 * 위 오류는 이 회차만 취소할 때(wholeSeries=false)다. 전체 취소는 기한이 지난 회차를 오류 없이 남기고 요청한 회차 카드를 돌려준다
 * (서버 BookingSeriesService.cancelWhole). 사유(reason)는 보내지 않는다 — 서버가 이 회차만이면 'customer', 전체면 'series_cancelled' 로 적는다
 */
export async function cancelReservation(id, { wholeSeries = false } = {}) {
  return http.post(`/customer/reservations/${encodeURIComponent(id)}/cancel`, { wholeSeries });
}

/** Settings — 헤더 · 프로필 · 메뉴 그룹 · 알림 설정 */
export async function getSettings() {
  return http.get('/customer/settings');
}

/** Settings › 알림 토글 저장 */
export async function updateNotificationSetting(key, enabled) {
  return http.put(`/customer/notifications/${encodeURIComponent(key)}`, { enabled });
}

/** Settings › 로그아웃. 서버 폐기가 실패해도 앱은 토큰을 지운다 */
/** 로그아웃 요청은 짧게 기다린다 — 서버가 느려도 5초 안에 세션을 지우고 화면을 넘긴다 */
const LOGOUT_TIMEOUT_MS = 5000;

/**
 * Settings › 로그아웃: ① 기기 푸시 토큰이 있으면 서버에서 지우고(DELETE /push/token) ② 서버에 이 로그인을 끝내라고 알린 뒤(POST /auth/logout)
 * ③ 가입 초안과 세션을 지운다. 서버 요청이 실패해도 ③ 은 한다.
 * 세션을 지우면 push/pushClient.js 가 기기의 푸시 등록을 푼다 — 401 강제 로그아웃(api/http.js 가 세션만 지움)과 같은 길이다.
 * generation = session.js 가 로그인·로그아웃 때마다 올리는 번호. 기다리는 사이 새로 로그인했으면(번호가 바뀜) 그 세션은 건드리지 않는다.
 */
export async function logout() {
  const generation = session.generation();
  deviceToken.suspend(true); // 그 사이 refresh 가 세션 구독을 깨워도 토큰을 다시 등록하지 않게
  try {
    if (session.accessToken() && deviceToken.get()) await http.delete('/push/token', { platform: deviceToken.platform(), token: deviceToken.get() }, { timeoutMs: LOGOUT_TIMEOUT_MS }); // 이 기기로 앞 사람의 푸시가 오지 않게
  } catch {
    // 실패해도 로그아웃은 진행 — 서버는 다음 사용자가 같은 토큰을 등록할 때 앞 사람 것을 지운다
  }
  try {
    // ① 을 기다리는 사이 계정이 바뀌었거나 세션이 이미 지워졌으면 보내지 않는다 (새 로그인을 끝내 버리지 않게)
    if (session.generation() === generation && session.accessToken()) await http.post('/auth/logout', undefined, { timeoutMs: LOGOUT_TIMEOUT_MS });
  } catch {
    // 서버가 죽어 있어도 로그아웃은 된다
  }
  deviceToken.suspend(false);
  if (session.generation() !== generation) return { ok: true }; // 이전 로그아웃의 늦은 응답이 새 로그인·초안을 지우지 않게
  clearSignupDraft();
  session.clear(); // 예약 초안(주소·요청사항)은 bookingDraft 가 세션 삭제를 구독해 지운다
  return { ok: true };
}

/** 예약 플로우 — 카테고리 정보 (라벨·프로모션 배너 종류). 모르는 카테고리면 null */
export async function getBookingCategory(category) {
  try {
    return await http.get(`/catalog/categories/${encodeURIComponent(category)}`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** Booking/Frequency — 주기 선택지 (Figma 문구, 로컬) */
export async function getFrequencyOptions() {
  return FREQUENCY_OPTIONS;
}

/** Booking/DateTime — 예약 가능 시간대 { am, pm, unavailable } */
// 응답에는 bookableDays(달력을 여는 기간)·changeDeadlineHours(변경·취소 기한 시간)도 있다 — 날짜 화면이 서버와 같은 기준으로 막고 경고한다
export async function getTimeSlots() {
  return http.get('/catalog/time-slots');
}

/** Booking/Step2 — 기존 주소 사용 여부 선택지 (로컬) */
export async function getAddressReuseOptions() {
  return ADDRESS_REUSE_OPTIONS;
}

/** Booking/AddressSearch — 주소 검색 → { query, results: [{ id, main, sub }] }. 서버가 카카오 로컬 API(§13-4)로 찾는다. 결과가 없으면 화면은 검색어를 그대로 주소로 쓴다 */
export async function searchAddress(query) {
  const q = (query ?? '').trim();
  if (q.length < 2) return { query: q, results: [] }; // 두 글자 미만이면 서버를 부르지 않는다
  return http.get(`/catalog/address?q=${encodeURIComponent(q)}`);
}

/** 예약 플로우 각 단계 저장. 초안은 sessionStorage(bookingDraft) 에만 두고 서버에는 완료 때 한 번 보낸다 */
export async function saveBookingStep(category, step, data) {
  return { category, step, data };
}

/** Booking/Options — 카테고리별 무료·유료 옵션. freeLimit = "택2" */
export async function getBookingOptions(category) {
  return http.get(`/catalog/categories/${encodeURIComponent(category)}/options`);
}

/** Booking/NotesRequests — 카테고리별 주의사항 칩 + 화면 문구 */
export async function getNoticeChips(category) {
  return http.get(`/catalog/categories/${encodeURIComponent(category)}/notice-chips`);
}

/** Booking/NotesRequests — 가입 때 받은 정보로 미리 골라 둘 주의사항 (지금은 반려동물). 실패해도 화면은 열린다 */
export async function getSuggestedNotices() {
  try {
    const res = await http.get('/customer/profile/suggested-notices');
    return res.notices ?? [];
  } catch {
    return [];
  }
}

/** Booking/Confirm·Booking/Frequency — 서비스 주기 부가 문구 · 화면 문구 · seriesTermMonths(정기청소 회차 수 = 개월 수) */
export async function getConfirmCopy(category) {
  return http.get(`/catalog/categories/${encodeURIComponent(category)}/confirm-copy`);
}

/** Booking/Step2 "네" 일 때의 기존 주소 = 회원 기본 주소. 없으면 null (화면은 새 주소 입력으로 안내) */
export async function getSavedAddress() {
  try {
    return await http.get('/customer/addresses/default');
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** 초안 → 서버 요청 본문 (견적·생성이 같은 필드를 보낸다) */
const bookingBody = (category, draft) => ({
  category,
  frequency: draft.frequency ?? 'recurring',
  date: draft.date,
  time: draft.time,
  freeOptions: draft.freeOptions ?? [],
  paidOptions: draft.paidOptions ?? [],
});

/**
 * Booking/Confirm 진입 시 견적 — { perVisit: { base, options, total, label }, visits: [{ date, label }], visitsPlanned, totalAmount, totalLabel, paymentNote }.
 * 저장 없음. 일정이 없으면 서버를 부르지 않고 null
 * 요금 내역 줄에 쓰는 perVisit.baseLabel·optionsLabel(유료 옵션이 없으면 null)과 savingNote(정기 할인 문구, 없으면 null)도 온다 (서버 QuoteResponse)
 */
export async function getBookingQuote(category, draft) {
  if (!draft.date || !draft.time) return null;
  return http.post('/customer/bookings/quote', bookingBody(category, draft));
}

/** Booking/Confirm 이 처음 "완료" 를 누를 때 만드는 멱등 키 (초안 submitKey 에 보관, §9.4) */
export const newSubmitKey = () => newIdempotencyKey();

/**
 * Booking/Confirm › "완료" — 예약 생성. 서버가 견적을 다시 계산해 저장한다.
 * draft.submitKey 를 Idempotency-Key 로 보내 재시도해도 예약이 두 번 생기지 않는다 (BACKEND_PLAN.md §9.4)
 */
export async function createBooking(category, draft) {
  if (!draft.date || !draft.time) throw new ApiError(400, 'VALIDATION', '방문 일정을 선택해주세요');
  if (!draft.roadAddress?.trim()) throw new ApiError(400, 'VALIDATION', '방문할 주소를 입력해주세요');
  const body = {
    ...bookingBody(category, draft),
    roadAddress: draft.roadAddress,
    detailAddress: draft.detailAddress ?? '',
    notices: draft.notices ?? [],
    request: draft.request ?? '',
    // 수리 종류(repairType)는 보내지 않는다 — 수리는 수리 예약 플로우의 createRepairBooking 이 따로 만든다 (서버에서는 비어 있음 = 청소)
    // Step2 에서 '기존 주소 사용' 에 아니요(no)를 골랐으면 true — 서버가 새로 입력한 주소를 주소 목록에 남긴다 (BookingService)
    saveAddress: draft.reuseAddress === 'no',
  };
  // submitKey 가 없으면 이번 호출에만 쓰는 새 키 — 화면이 다시 부르면 키가 달라 두 번 예약될 수 있다 (BookingConfirm 은 submitKey 를 초안에 둔다)
  return http.post('/customer/bookings', body, { idempotencyKey: draft.submitKey || newIdempotencyKey() });
}

/**
 * Booking/Complete — 예약 조회 (새로고침용). 없으면 ApiError(NOT_FOUND) → 화면이 ScreenError 로 보여준다.
 * 응답의 managerAssigned(매니저 배정됨)·matchingFailed(매칭에 실패해 멈춤)·cancelled(취소됨)로 완료 화면이 매니저 칸에 서버 문구(cleaner)를 쓸지 고른다 (api.md)
 */
export async function getBooking(id) {
  return http.get(`/customer/bookings/${encodeURIComponent(id)}`);
}

/** Booking/Complete — 카드 행 라벨 (로컬) */
export async function getCompleteRows() {
  return COMPLETE_ROWS;
}

/**
 * Service/Live* — 라이브 세션 정보 { manager, startedAt, viewers, status, reassurance, playback, live: { state, available, recordingReady } }.
 * 예약 id 없이 열린 화면(Figma 프로토타입 경로)은 mock 세션 + 대기 상태
 */
export async function getLiveSession(bookingId) {
  if (!bookingId) {
    if (import.meta.env.DEV) return { ...LIVE_SESSION, live: { state: 'waiting', available: false, recordingReady: false } }; // 갤러리·프로토타입 경로(DEV 만). 프로덕션은 화면이 /reservations 로 보낸다
    throw new ApiError(404, 'NOT_FOUND', '진행 중인 청소가 없어요');
  }
  return http.get(`/customer/bookings/${encodeURIComponent(bookingId)}/live`);
}

/** 고객 소유 예약의 현재 체크 상태·요청사항·다시보기 구간. 실시간 세로 화면은 5초마다 조회한다. */
export async function getLiveChecklist(bookingId) {
  if (!bookingId) {
    if (import.meta.env.DEV) return structuredClone(LIVE_CHECKLIST);
    throw new ApiError(404, 'NOT_FOUND', '진행 중인 청소가 없어요');
  }
  return http.get(`/customer/bookings/${encodeURIComponent(bookingId)}/checklist`);
}

/**
 * 청소 전·후 비교 사진 (2026-10-23, backend/docs/api.md '청소 전·후 사진 추가') → { ready, photos: [{ id, location, beforeUrl, afterUrl }] }.
 * ready = 매니저가 청소를 완료했다. 완료 전에는 { ready: false, photos: [] }. 사진 주소는 1시간짜리 서명 URL
 */
export async function getCleaningPhotos(bookingId) {
  return http.get(`/customer/bookings/${encodeURIComponent(bookingId)}/photos`);
}

/** 시청 토큰 { url, token, roomName }. 진행 중이 아니면 ApiError(LIVE_NOT_AVAILABLE) — liveClient 가 상태만 보여준다 */
export async function getLiveViewerToken(bookingId) {
  return http.post(`/live/sessions/${encodeURIComponent(bookingId)}/token`, { role: 'viewer' });
}

/** 녹화본 { url(서명 URL — 유효 시간은 서버 LiveService.RECORDING_URL_TTL_SECONDS), durationSeconds }. 준비 전이면 null */
export async function getRecording(bookingId) {
  try {
    return await http.get(`/live/sessions/${encodeURIComponent(bookingId)}/recording`);
  } catch (e) {
    if (e instanceof ApiError && e.code === 'RECORDING_NOT_READY') return null;
    throw e;
  }
}

// ── Settings 아래 화면들 (Figma 에 없음 — 코드에서 정함) ──
/** 약관 본문 { id, title, body } */
export async function getTerm(id) {
  return http.get(`/terms/${encodeURIComponent(id)}?audience=customer`);
}

/** 내 정보 수정 — 현재 값 { name, nickname, phone } / 저장(이름·닉네임만. 휴대폰 변경은 인증이 필요해 §13-7 전까지 서버가 무시한다) */
export async function getProfileForm() {
  return http.get('/customer/profile');
}
/** 내 정보 수정 › 저장 (EditProfile 화면). 이름·닉네임만 보낸다 */
export async function updateProfile({ name, nickname }) {
  return http.patch('/customer/profile', { name, nickname });
}

/** 주소 관리 — [{ id, label, roadAddress, detailAddress, isDefault }]. 첫 주소가 기본 주소, 기본 주소를 지우면 다음 주소가 기본 */
export async function getAddresses() {
  return http.get('/customer/addresses');
}
/** 주소 관리 › 주소 추가 (Addresses 화면). 별칭(label)을 비우면 '기타' 로 보낸다 */
export async function addAddress({ label, roadAddress, detailAddress }) {
  return http.post('/customer/addresses', { label: label || '기타', roadAddress, detailAddress });
}
/** 예약 "기존 주소를 사용할까요?" 에 쓸 기본 주소를 바꾼다 → 바뀐 목록 (직접 사용 점검 U15) */
export async function setDefaultAddress(id) {
  return http.put(`/customer/addresses/${encodeURIComponent(id)}/default`);
}
/** 주소 관리 › 주소 삭제 (Addresses 화면) */
export async function removeAddress(id) {
  return http.delete(`/customer/addresses/${encodeURIComponent(id)}`);
}

/** 결제 수단 — [{ id, label, isDefault }]. 결제(PG) 연동 전에는 서버가 항상 [] */
export async function getPaymentMethods() {
  return http.get('/customer/payment-methods');
}

/** 자주 묻는 질문 — [{ id, question, answer }] */
export async function getFaq() {
  return http.get('/support/faq?audience=customer');
}

/** Inquiry 화면이 문의 한 건마다 만드는 멱등 키 (실패 뒤 다시 보내면 같은 키, 내용을 고치면 새 키) */
export const newInquiryKey = () => newIdempotencyKey();

/** 1:1 문의 접수 → { id }. 공백만이면 서버가 VALIDATION. submitKey 를 Idempotency-Key 로 보내 재시도해도 두 번 접수되지 않는다 */
export async function submitInquiry({ content, submitKey }) {
  if (!content?.trim()) throw new ApiError(400, 'VALIDATION', '문의 내용을 입력해주세요');
  return http.post('/support/inquiries', { content: content.trim() }, { idempotencyKey: submitKey });
}

/** 내 문의와 답변 [{ id, content, status: open|answered, answer, createdLabel, answeredLabel }] — 1:1 문의 화면 위쪽 목록 */
export async function getMyInquiries() {
  return http.get('/support/inquiries');
}

/** 공지사항 목록 [{ id, title, date: "2026.09.10" }] / 본문 { id, title, date, body } */
export async function getNotices() {
  return http.get('/support/notices?audience=customer');
}
/** 공지 본문 한 건 (NoticeDetail 화면). 목록(getNotices)은 Notices 화면이 부른다 */
export async function getNotice(id) {
  return http.get(`/support/notices/${id}?audience=customer`);
}

/** 푸시 토큰 등록 (pushClient 가 기기 토큰을 받은 뒤·로그인 뒤) */
export async function registerPushToken({ platform, token }) {
  return http.put('/push/token', { platform, token });
}

// ── 수리 예약 (Figma rGCjx3zWMBk18fXgNZ9UYX) ──

/**
 * 가전 종류·증상·문구는 앱 안(Figma 문구). 시간대·현장 상황 칩은 청소 예약과 같은 서버 카탈로그(time_slots·catalog_notice_chips 'repair') — 운영자가 바꾸면 수리도 따라간다.
 * (검증 검토: 앱 사본과 서버 검증이 어긋나면 마지막 '예약하기' 에서야 SLOT_UNAVAILABLE 을 봤다)
 */
export async function getRepairBooking() {
  const [slots, chips] = await Promise.all([getTimeSlots(), getNoticeChips('repair')]);
  // 칩 목록·순서는 서버 것을 따르고, 확인 화면용 짧은 요약(summary)만 앱 안 문구에서 붙인다. 앱에 없는 키면 label 을 요약으로 쓴다
  const situations = chips.chips.map((c) => ({ key: c.key, label: c.label, summary: REPAIR_BOOKING.situations.find((s) => s.key === c.key)?.summary ?? c.label }));
  return { ...REPAIR_BOOKING, timeSlots: { am: slots.am, pm: slots.pm }, bookableDays: slots.bookableDays, changeDeadlineHours: slots.changeDeadlineHours, situations };
}

/** 증상 사진 업로드 → { path, url }. 서버가 Supabase Storage(repair-photos)에 저장한다 */
export async function uploadRepairPhoto(file) {
  // FormData 로 보내면 http.js 가 JSON 대신 파일 업로드(multipart)로 보내고, 더 오래(UPLOAD_TIMEOUT_MS) 기다린다
  const form = new FormData();
  form.append('file', file);
  return http.post('/customer/repair-photos', form);
}

/** Booking 과 같은 멱등 키 규칙 (§9.4) */
export const newRepairSubmitKey = () => newIdempotencyKey();

/**
 * 수리 예약 생성 — 서버 category 는 'repair', 수리 종류(가전수리 등)는 repairType, 가전·증상·상세·사진은 repair 로.
 * 현장 상황(situations)은 카탈로그 주의사항 키(notices)로 보낸다. 수리비는 현장 진단 후라 금액 0 (docs/api.md)
 */
export async function createRepairBooking(category, draft) {
  if (!draft.date || !draft.time) throw new ApiError(400, 'VALIDATION', '방문 일정을 선택해주세요');
  if (!draft.roadAddress?.trim()) throw new ApiError(400, 'VALIDATION', '방문할 주소를 입력해주세요');
  const body = {
    category: 'repair',
    frequency: 'oneTime',
    date: draft.date,
    time: draft.time,
    roadAddress: draft.roadAddress.trim(),
    detailAddress: draft.detailAddress?.trim() || null,
    freeOptions: [],
    paidOptions: [],
    notices: draft.situations ?? [],
    request: draft.request?.trim() || null,
    repairType: category,
    repair: {
      // 서버·기사에게는 표시 문구(카탈로그는 앱 안에 있다) + 키(통계·매칭용, V017)
      applianceKey: draft.appliance ?? null,
      symptomKey: draft.symptom ?? null,
      appliance: REPAIR_BOOKING.appliances.find((a) => a.key === draft.appliance)?.label ?? null,
      // 증상 목록은 가전마다 다를 수 있다(예: 냉장고). 따로 목록이 없는 가전은 공통 목록(default)에서 찾는다
      symptom: (REPAIR_BOOKING.symptoms[draft.appliance] ?? REPAIR_BOOKING.symptoms.default).find((s) => s.key === draft.symptom)?.label ?? null,
      detail: draft.detail?.trim() || null,
      photos: (draft.photos ?? []).map((p) => p.path), // 초안의 사진 { id, path, src } 중 저장 위치(path)만 보낸다. src 는 화면 미리보기용 주소
    },
    saveAddress: false,
  };
  return http.post('/customer/bookings', body, { idempotencyKey: draft.submitKey });
}

/** 고객센터 전화번호 (없으면 null). 운영 번호가 설정되기 전에는 앱이 전화 버튼을 그리지 않는다 (사용성 검토 2026-09-22) */
export async function getSupportPhone() {
  try {
    const res = await http.get('/support/contact');
    return res.phone ?? null;
  } catch {
    return null;
  }
}
