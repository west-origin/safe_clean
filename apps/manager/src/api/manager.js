// 매니저 앱 데이터 어댑터. 화면은 여기 함수만 부른다. (스키마: api/README.md)
// 서버 호출은 모두 http.js 를 거친다. 서버 모양과 앱 계약이 다르면 여기서 바꾼다
// (요일 번호, 반복 문구, 날짜·시각 형식, 서버가 null 로 주는 선택 문구 → undefined).
// 로그인·가입의 세션 저장(session.js)과 가입 초안(localStorage), DEV 확인용 ?scenario=empty|error 도 여기서 처리한다.
// 부르는 곳은 대부분 screens/* 화면이다. 화면 밖의 예: live/liveClient.js(라이브 세션 함수), App.jsx(refreshMe).
import { REPEAT_OPTIONS, SUPPORT_COPY, AUTH_COPY, MANAGER_PASSWORD_OK_MESSAGE } from './mock.js';
import { deviceToken } from '../push/deviceToken.js';

import { http, ApiError, newIdempotencyKey } from './http.js';
import { session } from './session.js';

/** 가입까지 끝난 로그인 상태 (RequireAuth). 토큰만 있고 가입이 덜 끝났으면 false → signupStep() */
export const isSignedIn = () => session.isSignedIn();
/** 세션 변화 구독 (RequireAuth) */
export const subscribeSession = session.subscribe;
/** 가입이 덜 끝난 로그인이면 다음 화면 키(서버 user.signupStep), 아니면 null. RequireAuth 가 이 값과 SIGNUP_STEP_PATHS 로 이어갈 화면을 고른다 */
export const signupStep = () => session.signupStep();
/** 매니저는 약관 → 정보 → 완료 뿐이라 재개 화면은 완료 하나 */
export const SIGNUP_STEP_PATHS = { complete: '/auth/signup/complete' };

/** 가입 초안 — localStorage: 가입 도중 앱을 닫았다 열어도 약관 동의가 남는다(sessionStorage 면 사라져 완료 요청이 빈 배열로 거부된다) */
const SIGNUP_DRAFT_KEY = 'ansimclean.manager.signup';
let signupDraftMemory = null; // 저장소가 막힌 환경의 사본 — 인증 토큰이 저장되지 않아 '인증을 먼저 하라' 가 반복되지 않게 (session.js 와 같은 규칙, 검수 3차)
/** 가입 초안 읽기 → { agreedTermIds?, verificationToken?, phone? } (없으면 {}) */
const readSignupDraft = () => {
  try {
    return JSON.parse(localStorage.getItem(SIGNUP_DRAFT_KEY)) ?? signupDraftMemory ?? {}; // 저장소가 진실, 메모리는 저장소가 비었거나 막혔을 때만
  } catch {
    return signupDraftMemory ?? {};
  }
};
/** 초안에 patch 를 합쳐 저장한다 (저장소와 메모리 사본 둘 다) */
const updateSignupDraft = (patch) => {
  const next = { ...readSignupDraft(), ...patch };
  signupDraftMemory = next;
  try {
    localStorage.setItem(SIGNUP_DRAFT_KEY, JSON.stringify(next));
  } catch {
    // 저장소를 쓸 수 없는 환경 — 메모리 사본으로 이번 실행 동안만 진행된다
  }
};
/** 초안을 지운다 — 계정이 바뀌거나(예: 로그인·로그아웃) 가입이 끝날 때. 공용 기기에서 앞 사람의 동의·인증 토큰이 남지 않게 */
const clearSignupDraft = () => {
  signupDraftMemory = null;
  try {
    localStorage.removeItem(SIGNUP_DRAFT_KEY);
  } catch {
    // 저장소를 쓸 수 없는 환경 — 로그아웃·탈퇴는 그래도 진행된다
  }
};

/** DEV 에서 ?scenario=empty 면 빈 상태, ?scenario=error 면 실패(ScreenError 확인용)를 돌려준다 */
const isEmptyScenario = () =>
  import.meta.env.DEV && new URLSearchParams(window.location.search).get('scenario') === 'empty';
const isErrorScenario = () => import.meta.env.DEV && new URLSearchParams(window.location.search).get('scenario') === 'error';
const failIfErrorScenario = () => {
  if (isErrorScenario()) throw new Error('DEV ?scenario=error');
};

/** 요일 라벨 ↔ 서버 weekday(0=월 … 6=일) */
const DAYS = ['월', '화', '수', '목', '금', '토', '일'];
/** 앱 반복 문구 ↔ 서버 repeat 값 (BACKEND_PLAN.md §9.0) */
const REPEAT_CODES = { '반복 없음': 'none', 매주: 'weekly', 매월: 'monthly' };

/**
 * 쓰기 요청의 멱등 키 (§9.4): 같은 대상(오퍼·작업)에 대한 첫 요청 때 만들고, 성공할 때까지 재시도에 같은 키를 보낸다.
 * 서버는 같은 키의 재요청에 저장된 응답을 돌려주므로 더블 탭·재시도로 두 번 처리되지 않는다
 * BACKEND_PLAN.md §9.4 는 '화면 진입 때 만들어 useRef 로 재사용' 이라고 적었지만, 실제 키는 이 모듈의 Map 이 대상별로 들고 있어
 * 화면을 떠났다 다시 와도 성공 전까지는 같은 키가 간다 (메모리라 앱을 다시 켜면 사라진다. 문의 키만 Support 화면의 useRef 다)
 */
const actionKeys = new Map();
// scope: 동작 + 대상 (예: 'accept:<콜 id>'). fn(key) 가 그 키를 붙여 실제 요청을 보낸다
async function withActionKey(scope, fn) {
  const key = actionKeys.get(scope) ?? newIdempotencyKey();
  actionKeys.set(scope, key);
  const result = await fn(key);
  // 성공했을 때만 지운다. fn 이 던지면 여기 오지 않아 키가 남고, 다시 누르면 같은 키로 재시도된다
  actionKeys.delete(scope);
  return result;
}

/** 수락이 늦어 서버가 거부했거나 이미 닫힌 콜을 열었을 때인가 (화면이 "이미 마감된 요청이에요" 를 보여준다). 거절은 지금 서버가 닫힌 콜에도 성공을 돌려줘 여기에 걸리지 않는다 */
export const isCallClosed = (error) => error?.code === 'OFFER_TAKEN' || error?.code === 'OFFER_EXPIRED';

/** 서버가 null 로 주는 선택 문구는 undefined 로 (CallCard 의 note 는 선택 prop) */
const callItem = (c) => ({ ...c, note: c.note ?? undefined });

/** Manager/Home — ?scenario=empty 면 카드 없는 홈 */
export async function getHome() {
  failIfErrorScenario(); // ?scenario=error 는 지금 이 함수(홈)에서만 흉내 낸다
  const res = await http.get('/manager/home');
  return isEmptyScenario() ? { ...res, inProgress: null, newCalls: null, remaining: [] } : res;
}

/** Manager/Call/List — pending 콜, 만료 임박순. tab 배지 = 건수 */
export async function getCalls() {
  const res = await http.get('/manager/offers');
  const calls = isEmptyScenario() ? [] : res.calls.map(callItem);
  return { ...res, calls, tabBadge: calls.length };
}

/** Manager/Call/Detail. 이미 닫힌 콜이면 ApiError(OFFER_EXPIRED) → 화면이 ScreenError */
export async function getCall(id) {
  return callItem(await http.get(`/manager/offers/${encodeURIComponent(id)}`));
}

/** 콜 거절 사유 목록 (CallList·CallDetail 의 거절 시트) */
export async function getRejectReasons() {
  return http.get('/catalog/reasons?kind=reject');
}

/**
 * 콜 거절 → { id, reason }. 이미 거절한 콜은 처음 사유 그대로(멱등).
 * 닫힌 콜은 ApiError — 다른 매니저 수락·고객 취소·만료면 OFFER_TAKEN | OFFER_EXPIRED(화면이 "이미 마감된 요청이에요" 시트), 내가 수락한 콜이면 INVALID_STATE(서버 문구)
 */
export async function rejectCall(id, reason) {
  return http.post(`/manager/offers/${encodeURIComponent(id)}/reject`, { reason });
}

/**
 * 콜 수락 (B4 추가, §9.2-2) → { jobId }. 앱은 /job/{jobId} 로.
 * 다른 매니저가 먼저 수락했거나 만료됐으면 ApiError(OFFER_TAKEN | OFFER_EXPIRED) — 화면이 "이미 마감된 요청이에요" 시트를 보여준다
 */
export async function acceptCall(id) {
  return withActionKey(`accept:${id}`, (key) => http.post(`/manager/offers/${encodeURIComponent(id)}/accept`, {}, { idempotencyKey: key }));
}

/** Manager/Job/Detail — 남의 작업은 404 */
export async function getJob(id) {
  return http.get(`/manager/jobs/${encodeURIComponent(id)}`);
}

/** Job/Preparing 장비 4행. 서버 값(바디캠 stream_status·last_report)이라 error 인자는 더 이상 쓰지 않는다 — Preparing/Error 화면도 실제 상태를 보여준다 */
export async function getEquipmentStatus() {
  return http.get('/manager/device/status');
}

/** Job/InProgress. 진행 중 작업이 없으면 ApiError(NOT_FOUND) */
export async function getActiveJob() {
  return http.get('/manager/jobs/active');
}

/** 체크리스트 항목 토글 → { jobId, itemId, done } */
export async function setChecklistItem(jobId, itemId, done) {
  return http.put(`/manager/jobs/${encodeURIComponent(jobId)}/checklist/${encodeURIComponent(itemId)}`, { done });
}

/** 촬영 일시정지 사유 목록 (Job/InProgress 의 일시정지 시트) */
export async function getPauseReasons() {
  return http.get('/catalog/reasons?kind=pause');
}

/**
 * Job/Preparing "청소 시작" (B4 추가, §9.2-3) → { jobId, startedAt }. scheduled 이고 작업 날짜가 오늘일 때만.
 * 화면 순서: startJob → live.start (작업 상태 먼저, 그 다음 라이브 세션)
 * 이 작업이 이미 진행 중(in_progress)이면 같은 응답, 다른 작업이 진행 중이면 서버가 INVALID_STATE 로 거절한다
 */
export async function startJob(id) {
  return withActionKey(`start:${id}`, (key) => http.post(`/manager/jobs/${encodeURIComponent(id)}/start`, {}, { idempotencyKey: key }));
}

/** 촬영 일시정지 ② (①은 live.pause). 영상이 아직 나가는 중이면 ApiError(INVALID_STATE "영상을 먼저 중단해주세요") */
export async function pauseJob(jobId, reason) {
  return http.post(`/manager/jobs/${encodeURIComponent(jobId)}/pause`, { reason });
}

/** Job/Paused — 중단 중인 작업이 없으면 ApiError(NOT_FOUND) */
export async function getPausedJob() {
  return http.get('/manager/jobs/active/pause');
}

/** 재개 ① (②는 live.resume). 멱등 */
export async function resumeJob(jobId) {
  return http.post(`/manager/jobs/${encodeURIComponent(jobId)}/resume`);
}

/** 청소 완료 → { jobId }. 정산이 같이 만들어진다 */
export async function completeJob(jobId) {
  return withActionKey(`complete:${jobId}`, (key) => http.post(`/manager/jobs/${encodeURIComponent(jobId)}/complete`, {}, { idempotencyKey: key }));
}

/** Job/Complete — 마지막으로 완료한 작업 */
export async function getCompletedJob() {
  return http.get('/manager/jobs/last-completed');
}

/** 정산 화면. 목록은 한 페이지(50건) — 더 있으면 nextBefore 커서로 다음 페이지 (검증 검토: 51번째부터 영영 볼 수 없었다) */
export async function getSettlement({ before } = {}) {
  return http.get(`/manager/settlements${before ? `?before=${encodeURIComponent(before)}` : ''}`);
}

/** Manager/Settlement/Detail — 정산 한 건 */
export async function getSettlementDetail(id) {
  return http.get(`/manager/settlements/${encodeURIComponent(id)}`);
}

/** Manager/Schedule — { items: 오늘, week: [{ dateKey, label, items }] } (기본 오늘~+6일. 달력 탭은 { from, to } 로 그 달 전체) */
export async function getSchedule({ from, to } = {}) {
  const query = from && to ? `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}` : '';
  const res = await http.get(`/manager/schedule${query}`);
  return isEmptyScenario() ? { items: [], week: [] } : res;
}

/** Schedule/Availability — { defaultHours: 요일별 기본 근무시간, unavailable: 근무 불가 목록 } */
export async function getAvailability() {
  return http.get('/manager/availability');
}

/** 반복 옵션 문구 (Figma) — 서버에는 none|weekly|monthly 로 보낸다 */
export async function getRepeatOptions() {
  return REPEAT_OPTIONS;
}

/** '2026.09.18' · '2026-09-18' · '20260918' → '2026-09-18'. 그 밖의 입력은 서버 문구('YYYY-MM-DD') 대신 화면 안내 형식으로 거절한다 */
const toIsoDate = (s) => {
  const d = (s ?? '').replace(/[^0-9]/g, '');
  const y = Number(d.slice(0, 4));
  const m = Number(d.slice(4, 6));
  const day = Number(d.slice(6));
  const real = new Date(Date.UTC(y, m - 1, day)); // 13월·32일처럼 없는 날짜는 서버 문구('YYYY-MM-DD') 대신 여기서 거른다
  if (d.length !== 8 || real.getUTCFullYear() !== y || real.getUTCMonth() !== m - 1 || real.getUTCDate() !== day) {
    throw new ApiError(400, 'VALIDATION', '날짜를 2026.09.18 형식으로 입력해주세요');
  }
  return `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6)}`;
};
/** '9:00' → '09:00'. 시:분이 아니면 VALIDATION */
const toHHmm = (s) => {
  const m = /^(\d{1,2}):(\d{2})$/.exec((s ?? '').trim());
  if (!m || Number(m[1]) > 23 || Number(m[2]) > 59) throw new ApiError(400, 'VALIDATION', '시간을 14:00 형식으로 입력해주세요');
  return `${m[1].padStart(2, '0')}:${m[2]}`;
};

/** 근무 불가 삭제 (검증 검토 — 잘못 넣은 날짜·'매주' 를 되돌릴 길이 없었다) */
export async function removeUnavailable(id) {
  await http.delete(`/manager/availability/unavailable/${encodeURIComponent(id)}`);
}

/** 근무 불가 시간 추가 → { id, date: "9월 18일", label, time, repeat } */
export async function addUnavailable({ date, allDay, start, end, repeat, reason }) {
  return http.post('/manager/availability/unavailable', {
    date: toIsoDate(date),
    allDay: Boolean(allDay),
    start: allDay ? null : toHHmm(start),
    end: allDay ? null : toHHmm(end),
    repeat: REPEAT_CODES[repeat] ?? repeat ?? 'none', // 화면 문구면 서버 값으로, 이미 서버 값이면 그대로, 없으면 반복 없음
    reason: reason || null, // 빈 사유('')는 null 로 보낸다
  });
}

/** Manager/Profile 화면 데이터 */
export async function getProfile() {
  return http.get('/manager/profile');
}

/** Profile/Equipment — 기기 미등록이면 ApiError(NOT_FOUND) (운영자가 등록) */
export async function getEquipmentDevice() {
  return http.get('/manager/device');
}

// ── 라이브 세션 (B5, docs/api.md §5.4). liveClient.js 만 부른다 ──
/** 방 생성 + 바디캠 Ingress 묶기 → { roomName, status, cameraOnline, cameraBusy, preview: { url, token } }. 같은 작업으로 다시 불러도 같다 */
export async function startLiveSession(jobId) {
  return http.post(`/live/sessions/${encodeURIComponent(jobId)}/start`);
}
/** 3초 폴링 → { status, cameraOnline, muted, cameraBusy, viewers, startedAt } */
export async function getLiveSessionStatus(jobId) {
  return http.get(`/live/sessions/${encodeURIComponent(jobId)}`);
}
/** 영상 차단 — 서버가 mute 를 확인한 뒤 200. 못 하면 ApiError(LIVE_PAUSE_UNCONFIRMED, 502) */
export async function pauseLiveSession(jobId) {
  return http.post(`/live/sessions/${encodeURIComponent(jobId)}/pause`);
}
/** 재개 ② 영상 재개. resumeJob(①)이 먼저 끝나야 한다 — 작업이 아직 paused 면 서버가 INVALID_STATE 로 거절한다 */
export async function resumeLiveSession(jobId) {
  return http.post(`/live/sessions/${encodeURIComponent(jobId)}/resume`);
}
/** 녹화 정지·방 닫기 */
export async function stopLiveSession(jobId) {
  return http.post(`/live/sessions/${encodeURIComponent(jobId)}/stop`);
}

// ── Figma 에 없는 동작·화면 (코드에서 정함) ──
/** Job/Detail › 지도 보기·고객 연락. 작업 당일 ±1일에만 서버가 준다 — 아니면 null (버튼 비활성) */
export async function getJobContact(jobId) {
  try {
    return await http.get(`/manager/jobs/${encodeURIComponent(jobId)}/contact`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}

/** Schedule/Availability › 기본 근무시간 수정. 앱 행 [{ day, start, end, off }] → 서버 [{ weekday, start, end, off }], 응답은 앱 행 모양 */
export async function updateDefaultHours(rows) {
  const body = rows.map((r) => ({ weekday: DAYS.indexOf(r.day), start: r.off ? null : toHHmm(r.start), end: r.off ? null : toHHmm(r.end), off: Boolean(r.off) })); // 화면이 start/end 를 그대로 준다 (문구 되파싱 없음)
  return http.put('/manager/availability/hours', body);
}

/** Profile › 매니저 정보·활동 지역·신원 인증·교육 이수·정산 계좌 (읽기 전용) */
export async function getProfileDetail(key) {
  return http.get(`/manager/profile/${encodeURIComponent(key)}`);
}

/** Profile › 알림 설정 { rows, values } */
export async function getManagerNotifications() {
  return http.get('/manager/notifications');
}
/** 알림 설정 한 항목(key)을 켜거나 끈다 */
export async function updateManagerNotification(key, enabled) {
  return http.put(`/manager/notifications/${encodeURIComponent(key)}`, { enabled });
}

/** Profile › 공지사항 [{ id, title, date: "2026.09.10" }] / 본문 { id, title, date, body } */
export async function getManagerNotices() {
  return http.get('/support/notices?audience=manager');
}
/** Profile › 공지사항 › 본문 한 건 */
export async function getManagerNotice(id) {
  return http.get(`/support/notices/${id}?audience=manager`);
}

/** 내 문의와 답변 [{ id, content, status, answer, createdLabel, answeredLabel }] */
export async function getMyInquiries() {
  return http.get('/support/inquiries');
}

/** Profile › 고객센터 · Job/Preparing/Error › 고객센터 문의 — 접수는 B6 에서 실서버 (POST /support/inquiries) */
export async function getSupportCopy() {
  return SUPPORT_COPY;
}
/** Support 화면이 문의 한 건마다 만드는 멱등 키 (실패 뒤 다시 보내면 같은 키, 내용을 고치면 새 키) */
export const newInquiryKey = () => newIdempotencyKey();

/** 고객센터 문의 → { id }. submitKey 를 Idempotency-Key 로 보내 재시도해도 두 번 접수되지 않는다 */
export async function submitSupportInquiry({ content, submitKey }) {
  if (!content?.trim()) throw new ApiError(400, 'VALIDATION', '문의 내용을 입력해주세요');
  return http.post('/support/inquiries', { content: content.trim() }, { idempotencyKey: submitKey });
}

/** 푸시 토큰 등록 (pushClient 가 기기 토큰을 받은 뒤·로그인 뒤) */
export async function registerPushToken({ platform, token }) {
  return http.put('/push/token', { platform, token });
}

// ── 로그인·회원가입 (Figma 에 화면 없음 → 코드에서 정함). 서버: docs/api.md §5.1 ──
/** Auth/Login · SignUp/* 문구 (앱 리소스) */
export async function getAuthCopy(screen) {
  const copy = AUTH_COPY[screen];
  if (!copy) throw new Error(`알 수 없는 인증 화면: ${screen}`);
  return copy;
}

/** Auth/Login — 휴대폰 번호(앞자리 010 제외) + 비밀번호. 틀리면 { ok: false }, 그 외 오류는 던진다 */
export async function loginWithPhone({ phone, password }) {
  try {
    const res = await http.post('/auth/login', { phone, password, role: 'manager' });
    clearSignupDraft(); // 공용 기기에서 다른 사람이 남긴 약관 동의·인증 토큰으로 가입을 끝내지 않게
    session.set(res);
    return { ok: true, user: res.user };
  } catch (e) {
    if (e instanceof ApiError && e.code === 'LOGIN_FAILED') return { ok: false };
    throw e;
  }
}

// ── Profile › 로그아웃. 서버 폐기가 실패해도 앱은 토큰을 지운다 ──
/** 로그아웃 요청은 짧게 기다린다 — 서버가 느려도 5초 안에 세션을 지우고 화면을 넘긴다 */
const LOGOUT_TIMEOUT_MS = 5000;

/**
 * 로그아웃 (Profile 화면). 순서: 푸시 재등록 멈춤 → 이 기기의 푸시 토큰을 서버에서 삭제 → 서버 로그아웃(이 로그인 하나만 끝낸다)
 * → 재등록 멈춤 해제 → 가입 초안·세션 삭제. 두 서버 요청은 실패해도 넘어가고 던지지 않는다.
 * 세션을 지우면 push/pushClient.js 가 기기의 푸시 등록을 푼다 — 401 강제 로그아웃(api/http.js 가 세션만 지움)과 같은 길이다.
 * 기다리는 사이 세션 번호가 바뀌었으면(다시 로그인 등) 그 세션은 지우지 않는다. 로그인 화면으로의 이동은 부르는 Profile 이 한다
 */
export async function logout() {
  const generation = session.generation(); // 시작 때의 세션 번호 — 기다리는 동안 다시 로그인했는지 알아보려고
  deviceToken.suspend(true); // 그 사이 refresh 가 세션 구독을 깨워도 토큰을 다시 등록하지 않게
  try {
    if (session.accessToken() && deviceToken.get()) await http.delete('/push/token', { platform: deviceToken.platform(), token: deviceToken.get() }, { timeoutMs: LOGOUT_TIMEOUT_MS }); // 이 기기로 앞 사람의 푸시가 오지 않게
  } catch {
    // 실패해도 로그아웃은 진행 — 서버는 다음 사용자가 같은 토큰을 등록할 때 앞 사람 것을 지운다
  }
  try {
    if (session.generation() === generation && session.accessToken()) await http.post('/auth/logout', undefined, { timeoutMs: LOGOUT_TIMEOUT_MS });
  } catch {
    // 서버가 죽어 있어도 로그아웃은 된다
  }
  deviceToken.suspend(false);
  if (session.generation() !== generation) return { ok: true }; // 이전 로그아웃의 늦은 응답이 새 로그인·초안을 지우지 않게
  clearSignupDraft();
  session.clear(); // liveClient 는 세션 삭제를 구독해 폴링·상태를 스스로 비운다
  return { ok: true };
}

/** SignUp/Terms — 약관 목록·안내 문구 { terms, note } */
export async function getManagerTerms() {
  return http.get('/terms?audience=manager');
}
/** 체크한 약관 id 를 초안에. 서버에는 가입 완료 때 보낸다 */
export async function agreeManagerTerms(agreedIds) {
  updateSignupDraft({ agreedTermIds: agreedIds });
  return { agreedIds };
}
/** 초안에 남은 약관 체크 (약관 본문을 보고 돌아왔을 때 복원) */
export function getAgreedTermsDraft() {
  return readSignupDraft().agreedTermIds ?? [];
}
/** SignUp/Terms › "보기>" — 약관 본문 { id, title, body } */
export async function getManagerTerm(id) {
  return http.get(`/terms/${encodeURIComponent(id)}?audience=manager`);
}

/** SignUp/Info › 비밀번호 검사 (8자 이상. 서버도 다시 검사) */
export async function checkPassword(password) {
  const ok = password.length >= 8;
  return { ok, message: ok ? MANAGER_PASSWORD_OK_MESSAGE : '' };
}
/** SignUp/Info › "인증하기". 이미 가입한 번호면 { sent: false, reason: 'taken' } */
export async function requestPhoneVerification(phone) {
  try {
    const res = await http.post('/auth/phone/otp', { phone, role: 'manager' });
    return { phone, sent: res.sent, expiresIn: res.expiresIn, resendAfter: res.resendAfter ?? 60 }; // 재전송 대기(초)가 응답에 없으면 60초
  } catch (e) {
    if (e instanceof ApiError && e.code === 'PHONE_TAKEN') return { phone, sent: false, reason: 'taken', message: e.message };
    throw e;
  }
}
/** SignUp/Info › 인증번호 확인 → 가입 토큰을 초안에 */
export async function verifyPhone(phone, code) {
  const res = await http.post('/auth/phone/verify', { phone, code, role: 'manager' });
  updateSignupDraft({ verificationToken: res.verificationToken, phone });
  return { ok: true };
}
/** Auth/ResetPassword (코드에서 정함) › "인증번호 받기" — 가입된 매니저 번호에만. 없으면 { sent: false, reason: 'notFound' } */
export async function requestPasswordReset(phone) {
  try {
    const res = await http.post('/auth/password/otp', { phone, role: 'manager' });
    return { phone, sent: res.sent, resendAfter: res.resendAfter ?? 60 };
  } catch (e) {
    if (e instanceof ApiError && e.code === 'PHONE_NOT_FOUND') return { phone, sent: false, reason: 'notFound', message: e.message };
    throw e;
  }
}
/** Auth/ResetPassword › 인증번호 확인 → 재설정 토큰 (가입 초안과 섞지 않는다) */
export async function verifyPasswordReset(phone, code) {
  const res = await http.post('/auth/phone/verify', { phone, code, role: 'manager' });
  return { verificationToken: res.verificationToken };
}
/** Auth/ResetPassword › 비밀번호 변경 → 바로 로그인 상태 */
export async function resetPassword({ verificationToken, password }) {
  const res = await http.post('/auth/password/reset', { verificationToken, password });
  clearSignupDraft(); // 공용 기기에서 다른 사람이 남긴 약관 동의로 가입을 끝내지 않게 — 로그인과 같다 (자체 점검 #39·#47)
  session.set(res);
  return res.user;
}
/** SignUp/Info › "다음" — 계정 생성 (가입 중 로그인 상태가 된다) */
export async function saveManagerSignUp({ name, password }) {
  const { verificationToken } = readSignupDraft();
  if (!verificationToken) throw new ApiError(400, 'OTP_EXPIRED', '휴대폰 인증을 먼저 해주세요'); // 서버와 같은 코드 — SignUpInfo 가 보고 인증 입력을 다시 연다
  const res = await http.post('/auth/signup', { verificationToken, name, password });
  session.set(res);
  // 계정을 만들었으니 인증 토큰은 초안에서 지운다. 약관 동의(agreedTermIds)는 가입 완료 때 보내야 하므로 남긴다
  updateSignupDraft({ verificationToken: null });
  return res.user;
}
/** SignUp/Complete › "시작하기" — 약관 동의를 보내고 가입을 끝낸다 */
export async function completeManagerSignUp() {
  const { agreedTermIds = [] } = readSignupDraft();
  // 다른 기기에서 이어가는 등 동의 기록이 없으면 서버가 '필수 약관' 으로 거부한다 — 서버에 보내기 전에 약관 화면으로 돌려보낸다
  if (agreedTermIds.length === 0) throw new ApiError(0, 'TERMS_REQUIRED', '약관 동의가 필요해요'); // 서버 응답이 아니라 status 0. SignUpComplete 가 이 코드를 보고 약관 화면으로 보낸다
  const res = await http.post('/auth/signup/complete', { agreedTermIds });
  session.setUser(res.user);
  clearSignupDraft();
  return { ok: true, user: res.user };
}

/** 앱 시작 시 한 번: 서버 기준으로 세션 사용자 갱신 */
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

/** Profile › 회원 탈퇴 (비밀번호 확인). 진행 중 작업이 있으면 ApiError(INVALID_STATE) */
export async function deleteAccount(password) {
  // 비밀번호가 틀리면 서버가 401 LOGIN_FAILED — http.js 는 이것을 토큰 문제로 보지 않아 세션을 지우지 않고 그대로 던진다
  await http.delete('/auth/account', { password });
  clearSignupDraft();
  session.clear(); // liveClient 는 세션 삭제를 구독해 폴링·상태를 스스로 비운다
  return { ok: true };
}

/** Job/Detail › "방문 취소" (매니저 정책, docs/manager-policy.md) — 예약은 취소되지 않고 다시 매칭된다 */
export async function cancelJob(id, { reason, detail }) {
  // 멱등 키 — 응답이 늦어 다시 눌러도 두 번 처리되거나 '찾을 수 없어요' 가 뜨지 않게 (자체 점검 #44)
  // 키는 작업마다 하나다. 서버는 처리했는데 응답만 잃은 뒤 사유를 바꿔 다시 보내면 IDEMPOTENCY_MISMATCH 가 된다
  return withActionKey(`cancel:${id}`, (key) => http.post(`/manager/jobs/${encodeURIComponent(id)}/cancel`, { reason, detail: detail || null }, { idempotencyKey: key }));
}

/** 방문 취소 사유 목록 (서버 설정) */
export async function getCancelReasons() {
  return http.get('/manager/cancel-reasons');
}

/** MY › 패널티 내역 { points, nextThreshold, suspension, items[] } */
export async function getPenalties() {
  return http.get('/manager/penalties');
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
