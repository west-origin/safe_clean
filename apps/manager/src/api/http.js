// 서버 호출은 전부 여기를 거친다. 화면은 이 파일을 모른다 (api/*.js 의 함수만 부른다).
//  - Authorization: Bearer <accessToken> 을 붙이고, 401 이면 refresh 를 한 번 시도한 뒤 재시도한다. refresh 가 401 이면 세션을 지운다.
//    refresh 가 네트워크·서버 오류(429·5xx)로 실패한 것은 토큰이 무효한 게 아니다 — 세션을 지우지 않고 NETWORK 로 던진다(잠깐의 장애가 강제 로그아웃이 되지 않게).
//    정확히는 refresh 응답이 401 이 아니면(예: 400·403 도) 모두 이쪽이다. 세션을 지우는 것은 토큰이 거부된 뒤 refresh 도 401 이거나
//    refresh 토큰이 없을 때, 또는 갱신한 토큰으로 다시 보낸 요청도 401 일 때뿐이다 (refreshSession·request).
//  - 서버 오류 { error: { code, message } } 는 ApiError(status, code, message) 로 던진다. 화면의 useAsync/useAction 이 받는다.
//  - 401 이어도 code 가 LOGIN_FAILED(비밀번호 확인 실패: 탈퇴·로그인)면 토큰 문제가 아니다 — refresh 도 세션 삭제도 하지 않는다.
//  - 네트워크 실패·응답 없음(REQUEST_TIMEOUT_MS)은 ApiError(0, 'NETWORK', …). 세션은 그대로.
// 부르는 곳: api/manager.js 의 함수들 (화면·liveClient·pushClient 는 manager.js 를 통해서만 서버에 닿는다).
// "generation" = session.js 가 로그인(set)과 세션 삭제(clear: 로그아웃·탈퇴·토큰 거부) 때마다 1씩 올리는 번호.
// 요청을 보낸 뒤 이 번호가 바뀌었으면 그 사이 로그인 상태가 바뀐 것이므로, 늦게 온 응답을 버리고
// (401 UNAUTHENTICATED 로 던진다) 토큰 갱신·재시도도 하지 않는다.
import { session } from './session.js';

/** 비우면 같은 origin 의 /api/v1 (.env.example 과 같은 규칙). Capacitor 앱은 origin 이 capacitor://localhost 라 반드시 채워야 한다 */
const BASE = ((import.meta.env.VITE_API_URL ?? '').trim() || '/api/v1').replace(/\/$/, ''); // 끝의 / 는 떼어 낸다 (주소가 '…//manager' 가 되지 않게)
/** 응답이 이만큼 없으면 NETWORK 로 끝낸다 — 버튼이 영원히 로딩으로 남지 않게 */
const REQUEST_TIMEOUT_MS = 20000;
/** 파일 업로드(multipart)는 본문이 커서 더 기다린다 — 느린 망에서 사진 한 장이 20초를 넘겨 '네트워크' 로 끝나던 것 (검수 3차) */
const UPLOAD_TIMEOUT_MS = 90000;
/** DEV 전용: `VITE_API_MOCK=1` 이면 서버 없이 테스트용 가짜 서버(src/test/fakeHttp.js)로 화면을 본다. 프로덕션 빌드에는 들어가지 않는다 */
const useFake = import.meta.env.DEV && import.meta.env.VITE_API_MOCK === '1';
let fakePromise = null;
// 가짜 서버는 처음 요청 때 한 번만 불러와 계속 같은 것을 쓴다 (??= : 비어 있을 때만 채운다). 그래야 가짜 서버의 상태가 요청 사이에 이어진다
const fake = () => (fakePromise ??= import('../test/fakeHttp.js').then((m) => m.fakeHttpModule().http));

/**
 * 서버 호출 실패. 화면은 code 로 경우를 나누고, message 는 화면에 그대로 보여도 되는 한국어 문구다 (ActionError 가 보여 준다).
 * status: HTTP 상태 (0 = 서버 응답을 못 받음: 네트워크 실패·시간 초과, 또는 토큰 갱신이 429·5xx 로 실패함).
 *   manager.js 가 보내기 전에 막을 때는 대개 400(예: VALIDATION)이고, 약관 동의 없음(TERMS_REQUIRED)만 0 이다
 * code: 서버 오류 코드, 또는 앱이 붙인 'NETWORK' · 'UNAUTHENTICATED'(요청 도중 로그인 상태가 바뀜) · 'HTTP_<상태>'(오류 본문이 없을 때)
 */
export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

/** 요청 중복 방지용 키. 같은 요청의 재시도 때 같은 키를 다시 보낸다 (BACKEND_PLAN.md §9.4) */
// 매니저 앱에서 만드는 곳: 수락·시작·완료·방문 취소는 manager.js 의 withActionKey(대상별), 문의는 Support 화면(newInquiryKey)
// crypto.randomUUID 가 없는 환경(localhost 가 아닌 http:// 주소, 오래된 WebView)에서는 시각 + 난수로 대신 만든다
export const newIdempotencyKey = () => (globalThis.crypto?.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

let refreshing = null; // 동시에 여러 요청이 401 을 받아도 refresh 는 한 번만

// access 토큰이 401 로 거부됐을 때 refresh 토큰으로 새 토큰을 받는다 (POST /auth/refresh). 성공하면 session.renew 로 저장한다.
// generation: 원래 요청을 보낼 때의 세션 번호. 그 사이 로그인·로그아웃이 있었으면 갱신하지 않는다.
/** @returns {Promise<'ok'|'invalid'|'transient'>} invalid = refresh 토큰이 무효(세션 지움) · transient = 네트워크·서버 장애(세션 유지) */
async function refreshSession(generation) {
  if (session.generation() !== generation) return 'invalid';
  // 같은 세션 번호의 갱신이 이미 진행 중이면 새로 보내지 않고 그 결과를 같이 기다린다
  if (!refreshing || refreshing.generation !== generation) {
    const flight = { generation };
    refreshing = flight;
    flight.promise = (async () => {
      const token = session.refreshToken();
      if (!token) return 'invalid';
      const got = await fetchWithTimeout(`${BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: token }),
      }).catch(() => null);
      if (!got) return 'transient';
      // 기다리는 사이 세션이 바뀌었으면(로그아웃·다른 계정 로그인·토큰 교체) 이 응답은 저장하지 않는다
      if (session.generation() !== generation || session.refreshToken() !== token) return 'invalid';
      const { res, text } = got;
      if (res.ok) {
        // 본문이 JSON 이 아니면(프록시 오류 페이지 등) 토큰 문제로 보지 않고 transient 로 둔다
        try {
          if (!session.renew(JSON.parse(text), generation)) return 'invalid';
        } catch {
          return 'transient';
        }
        return 'ok';
      }
      // refresh 토큰까지 거부됐다 = 다시 로그인해야 한다. 세션을 지우면 RequireAuth 가 로그인 화면으로 보낸다
      if (res.status === 401) {
        session.clear();
        return 'invalid';
      }
      return 'transient'; // 429·5xx — 토큰은 아직 유효할 수 있다
    })().finally(() => {
      // 끝나면 비운다. 그 사이 다른 세션 번호의 갱신이 자리를 차지했으면 그것은 건드리지 않는다
      if (refreshing === flight) refreshing = null;
    });
  }
  return refreshing.promise;
}

/** 응답 헤더뿐 아니라 본문까지 REQUEST_TIMEOUT_MS 안에 받는다 — 본문 전송이 멈춰도 버튼이 영원히 로딩으로 남지 않게. { res, text } 를 돌려준다 */
async function fetchWithTimeout(url, init, timeoutMs = REQUEST_TIMEOUT_MS) {
  // AbortController: 시간이 다 되면 abort() 로 진행 중인 fetch 를 오류로 끝낸다 (본문 읽기 중이어도)
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...init, signal: controller.signal });
    const text = res.status === 204 ? '' : await res.text(); // 204(No Content)는 본문이 없다
    return { res, text };
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 서버 요청 하나. 성공하면 JSON 본문(204 면 null), 실패하면 ApiError 를 던진다.
 * @param {string} method 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
 * @param {string} path BASE 뒤에 붙는 경로 (예: '/manager/home')
 * @param {object|FormData} [body] 없으면 본문을 보내지 않는다. FormData 면 파일 업로드로 보낸다
 * @param {object} [opts] idempotencyKey: Idempotency-Key 헤더 · retry: 재시도 허용(두 번째 시도는 false 라 한 번만 다시 한다)
 *   · timeoutMs: 기다릴 시간 · generation: 첫 시도 때의 세션 번호 (재시도가 같은 번호를 이어받는다)
 *   retry 는 토큰 갱신 뒤 재시도와 IN_PROGRESS 1초 뒤 재시도가 같이 쓴다 — 한 요청에서 둘 중 한 번만 다시 보낸다
 */
async function request(method, path, body, { idempotencyKey, retry = true, timeoutMs, generation = session.generation() } = {}) {
  // 요청 도중 로그인·로그아웃이 있었으면 멈춘다 — 이전 계정의 응답을 새 화면에 넘기지 않게
  const requireSameSession = () => {
    if (session.generation() !== generation) throw new ApiError(401, 'UNAUTHENTICATED', '로그인 상태가 바뀌었어요. 다시 시도해주세요');
  };
  requireSameSession();
  // DEV 가짜 서버: 헤더·시간 제한·토큰 갱신 없이 가짜 서버 함수에 바로 넘긴다.
  // 가짜 서버(test/fakeHttp.js)는 이 파일을 import 하면 순환이 생겨 ApiError 를 같은 모양으로 따로 정의한다 — 그 오류를
  // 이 파일의 ApiError 로 바꿔 던져야 manager.js 의 `e instanceof ApiError`(예: 로그인 LOGIN_FAILED → 로그인 실패 안내)가
  // 실제 서버일 때처럼 동작한다 (문제 목록 37번). 가짜 서버 코드의 실수(TypeError 등)는 감추지 않고 그대로 던진다
  if (useFake) {
    try {
      return await (await fake())[method.toLowerCase()](path, body, { idempotencyKey });
    } catch (e) {
      if (e?.name === 'ApiError' && !(e instanceof ApiError)) throw new ApiError(e.status, e.code, e.message);
      throw e;
    }
  }
  const headers = { Accept: 'application/json' };
  // 지금 매니저 앱 코드에는 FormData 를 보내는 호출이 없다 (업로드가 생기면 이 분기가 쓰인다)
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData; // 파일 업로드 — Content-Type 은 브라우저가 boundary 와 함께 붙인다
  if (body !== undefined && !multipart) headers['Content-Type'] = 'application/json';
  const token = session.accessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  let res;
  let text;
  try {
    ({ res, text } = await fetchWithTimeout(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body) }, timeoutMs ?? (multipart ? UPLOAD_TIMEOUT_MS : REQUEST_TIMEOUT_MS)));
  } catch {
    throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요');
  }
  requireSameSession(); // 늦은 성공·401 모두 이전 계정의 화면/세션으로 전달하거나 새 계정으로 재시도하지 않는다.
  if (res.status === 204) return null;

  // 본문이 JSON 이 아니면(프록시·게이트웨이의 HTML 오류 페이지 등) null 로 두고, 아래에서 상태 코드로 문구를 고른다
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  const err = data?.error ?? {};
  const tokenRejected = res.status === 401 && token && err.code !== 'LOGIN_FAILED'; // 비밀번호 확인 실패는 토큰 문제가 아니다
  // 토큰 거부 → 한 번만 갱신해 보고, 'ok' 면 같은 요청을 retry: false 로 다시 보낸다.
  // 'invalid' 인데 그 사이 세션 번호가 바뀌었으면(refreshSession 이 세션을 지운 경우 포함) 바로 아래
  // requireSameSession 이 401(UNAUTHENTICATED)을 던진다. 번호가 그대로면 아래 !res.ok 로 내려가
  // (access 토큰이 그대로일 때만) 세션을 지우고 서버가 준 401 을 던진다
  if (tokenRejected && retry) {
    const refreshed = await refreshSession(generation);
    requireSameSession();
    if (refreshed === 'ok') return request(method, path, body, { idempotencyKey, retry: false, timeoutMs, generation });
    if (refreshed === 'transient') throw new ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요'); // 세션은 그대로 — 다음 요청이 다시 시도한다
  }
  if (!res.ok) {
    if (tokenRejected && session.accessToken() === token) session.clear(); // 기다리는 사이 다른 계정으로 로그인했으면 그 세션은 건드리지 않는다 (외부 검토 4차)
    // 같은 키로 처리 중인 요청이 있으면 1초 뒤 한 번 더 (서버 §6.9 IN_PROGRESS)
    if (res.status === 409 && err.code === 'IN_PROGRESS' && idempotencyKey && retry) {
      await new Promise((r) => setTimeout(r, 1000));
      return request(method, path, body, { idempotencyKey, retry: false, timeoutMs, generation });
    }
    throw new ApiError(res.status, err.code ?? 'HTTP_' + res.status, err.message ?? defaultMessage(res.status));
  }
  // 성공(2xx)인데 본문이 JSON 이 아니다 = 서버가 아닌 곳이 답했다. 예: Capacitor 앱에서 VITE_API_URL 이 비어 앱 자신의 index.html 이 왔다.
  // null 을 돌려주면 부른 쪽이 엉뚱한 곳에서 터진다(로그인의 session.set(null) → "Cannot destructure property 'accessToken'") — 여기서 오류로 끝낸다
  if (text && data === null) throw new ApiError(res.status, 'BAD_RESPONSE', defaultMessage(res.status));
  return data;
}

/**
 * 서버가 우리 오류 모양(JSON)을 주지 않은 응답의 문구. 예전에는 전부 "잠시 후 다시 시도해주세요" 라
 * 원인을 짐작할 수 없었다 (사용성 검토 2026-09-22). 상태 코드로 아는 만큼만 말한다.
 */
function defaultMessage(status) {
  if (status === 404) return '요청한 정보를 찾을 수 없어요';
  if (status === 408 || status === 504) return '서버 응답이 늦어요. 잠시 후 다시 시도해주세요';
  if (status === 429) return '요청이 너무 많아요. 잠시 후 다시 시도해주세요';
  if (status >= 500) return '서버에 문제가 생겼어요. 잠시 후 다시 시도해주세요';
  return '요청을 처리하지 못했어요. 잠시 후 다시 시도해주세요';
}

/**
 * manager.js 가 쓰는 요청 함수 묶음. manager.test.js 같은 테스트는 vi.mock 으로 이 모듈을 test/fakeHttp.js 로 바꿔 끼운다.
 * post·put·patch 는 본문이 없으면 {} 를 보낸다 (항상 JSON). get 은 본문·옵션이 없고, delete 는 본문이 있을 때만 보낸다.
 */
export const http = {
  get: (path) => request('GET', path),
  post: (path, body, opts) => request('POST', path, body ?? {}, opts),
  put: (path, body, opts) => request('PUT', path, body ?? {}, opts),
  patch: (path, body, opts) => request('PATCH', path, body ?? {}, opts),
  delete: (path, body, opts) => request('DELETE', path, body, opts),
};
