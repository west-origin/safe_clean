// 로그인 세션 보관 (토큰 + 사용자). 화면 컴포넌트는 이 파일을 직접 쓰지 않는다
// — api/customer.js 의 isSignedIn()·signupStep()·subscribeSession 을 쓴다.
// 네이티브 앱(Capacitor)은 capacitor-secure-storage-plugin(iOS Keychain / Android Keystore 로 암호화한 SharedPreferences)에, 웹 개발 서버는 localStorage 에 둔다 (BACKEND_PLAN.md 결정 10, B7 전).
// WKWebView 의 localStorage 는 저장 공간 압박 때 지워질 수 있어 사용자가 이유 없이 로그아웃됐다 (완결성 검토). 평문 UserDefaults/SharedPreferences 는 탈옥·루팅 기기와 iOS 백업에서 읽혀
// Keychain/Keystore 로 옮겼다 (보안 검토 2026-09-22). 네이티브는 **보안 저장소에만** 둔다 — localStorage 평문 사본·삭제 실패 뒤 옛 세션 복원을 없앴다 (감사 M-01).
// 읽기는 비동기라 App 이 hydrate() 를 기다린 뒤 라우터를 그린다.
// 직접 import 하는 곳 예: api/http.js(토큰 붙이기·refresh), App.jsx(hydrate). 화면 폴더에서는 screens/booking/bookingDraft.js 가
// 예외로 직접 구독해 세션이 지워지면(로그아웃·탈퇴·401) 예약 초안을 지운다. 매니저 앱의 api/session.js 도 같은 방식이다(예약 초안 삭제만 없다).
// 흐름: 메모리(memory)가 기준이고, 바뀔 때마다 write() 로 저장소에 옮기고 notify() 로 구독자(RequireAuth 등)에 알린다.
import { Capacitor } from '@capacitor/core';

const NATIVE = Capacitor.isNativePlatform(); // Capacitor 로 감싼 iOS·Android 앱 안이면 true, 브라우저면 false
let secure = null; // capacitor-secure-storage-plugin 의 SecureStoragePlugin (네이티브에서만 로드)
const KEY = 'ansimclean.customer.auth'; // 세션 저장 키. 웹은 localStorage, 네이티브는 보안 저장소에서 같은 이름을 쓴다

/** localStorage 의 세션을 읽는다. 없거나 JSON 이 깨졌거나 저장소를 쓸 수 없으면 null. 네이티브에서는 옛 평문 세션 이전용으로만 쓴다 */
function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? null;
  } catch {
    return null;
  }
}

// 네이티브 쓰기가 끝났는지만 적는 표식(비밀 아님). 저장·삭제가 실패하거나 도중에 앱이 죽으면 남아, 다음 실행에서 옛 Keychain 세션을 읽지 않는다(로그인부터 다시)
const DIRTY = `${KEY}.pending`;
let storageWrites = Promise.resolve(); // 네이티브 저장소 쓰기 줄(큐). 새 쓰기는 앞 쓰기가 끝난 뒤에 시작한다
let revision = 0; // 네이티브 쓰기마다 1 늘린다. 늦게 끝난 옛 쓰기가 대기 표식을 끄거나, 늦게 끝난 읽기가 새 메모리를 덮지 않게 비교하는 번호
let generation = 0; // 로그인·로그아웃 경계. 같은 계정의 토큰 갱신은 바꾸지 않는다.

/** 쓰기 대기 표식(DIRTY)을 켜거나 끈다. 켜져 있으면 다음 실행의 hydrate 가 보안 저장소를 믿지 않는다 */
function markPending(pending) {
  try {
    if (pending) localStorage.setItem(DIRTY, '1');
    else localStorage.removeItem(DIRTY);
  } catch {
    // 저장소가 막힌 실행에서는 메모리 세션만 사용할 수 있다.
  }
}

/**
 * 보안 저장소에 세션을 쓰거나(value 있음) 지운다(null). 앞선 쓰기가 끝난 뒤에 실행되도록 줄을 세운다.
 * current 가 지금 revision 과 같을 때만(그 사이 새 쓰기가 없을 때만) 대기 표식을 끈다. 실패하면 거부된 Promise 를 돌려준다.
 */
function persistNative(value, current) {
  const pendingWrite = storageWrites.catch(() => {}).then(async () => {
    if (value) await secure.set({ key: KEY, value: JSON.stringify(value) });
    else await secure.remove({ key: KEY });
    if (current === revision) markPending(false);
  });
  storageWrites = pendingWrite.catch(() => {}); // 실패하면 pending 표식을 남긴다.
  return pendingWrite;
}

/**
 * 메모리 세션(value, 로그아웃이면 null)을 저장소에 반영한다. 웹은 localStorage, 네이티브는 보안 저장소.
 * 네이티브는 먼저 대기 표식을 켜 두고 쓰기가 끝나면 끈다 — 도중에 실패·종료돼도 다음 실행이 옛 세션을 되살리지 않게.
 */
function write(value) {
  if (NATIVE) {
    const current = ++revision;
    markPending(true);
    try { localStorage.removeItem(KEY); } catch { /* plaintext fallback 금지 */ }
    // secure 는 hydrate() 가 플러그인을 불러온 뒤에만 있다. 그 전의 쓰기는 보안 저장소에 가지 않고 대기 표식만 남는다
    if (secure) {
      // 로그인/refresh/로그아웃 쓰기 순서를 보장한다.
      persistNative(value, current).catch(() => {});
    }
    return;
  }
  try {
    if (value) localStorage.setItem(KEY, JSON.stringify(value));
    else localStorage.removeItem(KEY);
  } catch {
    // 웹 저장소를 쓸 수 없으면 이번 실행 동안만 유지된다.
  }
}

// 지금 세션(메모리). 웹은 모듈이 로드될 때 바로 localStorage 에서 읽고, 네이티브는 hydrate() 가 채운다
let memory = NATIVE ? null : read();
let hydrated = !NATIVE; // 웹은 바로 위에서 localStorage 를 동기로 이미 읽었으니 처음부터 끝난 것으로 둔다
let hydrating = null; // 진행 중인 hydrate 의 Promise — 여러 번 불려도 한 번만 읽는다

/** 네이티브: 보안 저장소를 읽고 기존 평문 세션은 한 번만 이전한다. */
async function hydrate() {
  if (hydrated) return;
  if (hydrating) return hydrating;
  hydrating = (async () => {
    // 읽는 동안 세션 쓰기(write: 로그인·로그아웃·토큰 갱신 등)가 끼어들면 revision 이 바뀐다 — 그때는 읽은 옛 값으로 메모리를 덮지 않는다
    const current = revision;
    const legacy = read(); // 예전 설치가 localStorage 에 남긴 평문 세션 (없으면 null)
    let pending = true;
    try { pending = localStorage.getItem(DIRTY) === '1'; } catch { /* fail closed */ }
    try { localStorage.removeItem(KEY); } catch { /* 평문을 새로 쓰지 않는다 */ }
    try {
      // 플러그인은 네이티브에서만 필요해 여기서 늦게 불러온다 (import() = 필요할 때 모듈을 비동기로 읽기)
      secure = (await import('capacitor-secure-storage-plugin')).SecureStoragePlugin;
      // 지난 실행의 쓰기가 끝나지 않았으면(pending) 보안 저장소 값이 옛 세션일 수 있어 읽지 않는다 → 로그인부터 다시
      if (!pending && current === revision) {
        const value = await secure.get({ key: KEY }).then((r) => r.value, () => null); // 저장된 값이 없으면 플러그인이 거부한다 → null
        if (current === revision) {
          // 보안 저장소 값이 우선. 없으면 옛 평문 세션을 쓰고, 그것을 보안 저장소로 옮긴다
          memory = value ? JSON.parse(value) : legacy;
          if (!value && legacy) {
            markPending(true);
            await persistNative(legacy, current); // 이전도 같은 큐: 늦은 저장이 로그아웃 삭제를 추월하지 않게
          }
        }
      }
    } catch {
      if (current === revision) memory = null; // 오래된 이전 실패가 새 로그인 메모리를 지우지 않게
    }
    hydrated = true;
    notify(); // 읽기가 끝났음을 구독자에게 알린다 (성공·실패 모두)
  })();
  return hydrating;
}
// subscribe() 로 등록한 함수들. 세션이 바뀔 때마다 notify() 가 전부 부른다
const listeners = new Set();
const notify = () => listeners.forEach((fn) => fn());

/** 예약 초안 키 접두어 (screens/booking/bookingDraft.js 와 같다). 로그아웃·탈퇴·401 이면 전부 지운다 — 같은 기기에서 계정을 바꿔도 이전 사람의 주소가 보이지 않게 */
const DRAFT_PREFIX = 'ansimclean.booking.';
/** sessionStorage 에서 예약 초안 키를 전부 지운다 (clear() 가 부른다) */
function clearSessionDrafts() {
  try {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith(DRAFT_PREFIX))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    // 저장소를 쓸 수 없는 환경
  }
}

/** 세션 보관 객체. 세션을 바꾸는 메서드(예: set·clear)는 메모리를 바꾸고 → 저장소에 쓰고(write) → 구독자에게 알린다(notify) */
export const session = {
  /** 지금 로그인 경계 번호. http.js 가 요청 시작 때 적어 두고, 응답이 왔을 때 같은 로그인인지 비교한다 */
  generation: () => generation,
  /** 앱 시작 때 App.jsx 가 한 번 기다린다 (네이티브만 실제로 읽는다) */
  hydrate,
  /** @returns {{ accessToken: string, refreshToken: string, expiresAt: number, user: object } | null} */
  get: () => memory,
  /** 서버의 로그인·가입 응답을 저장한다. 같은 로그인의 refresh는 renew를 사용한다. */
  set({ accessToken, refreshToken, expiresIn, user }) {
    // 새 로그인 경계 — 이전 로그인에서 보낸 요청의 늦은 응답(refresh 포함)이 이 세션을 바꾸지 못하게 번호를 올린다
    generation++;
    // expiresIn(초)을 만료 시각(ms)으로 바꿔 둔다. user 가 응답에 없으면 지금 들고 있던 user 를 유지한다
    memory = { accessToken, refreshToken, expiresAt: Date.now() + expiresIn * 1000, user: user ?? memory?.user ?? null };
    write(memory);
    notify();
  },
  /** 같은 로그인 세션의 refresh 응답만 반영한다. 로그아웃·새 로그인 경계를 넘으면 거부한다. */
  renew({ accessToken, refreshToken, expiresIn, user }, expectedGeneration) {
    // expectedGeneration = refresh 를 시작할 때의 번호 (http.js refreshSession). 결과: 반영했으면 true
    if (generation !== expectedGeneration || !memory) return false;
    memory = { accessToken, refreshToken, expiresAt: Date.now() + expiresIn * 1000, user: user ?? memory.user };
    write(memory);
    notify();
    return true;
  },
  /** 사용자 정보만 갱신 (GET /auth/me, 가입 완료) */
  setUser(user) {
    if (!memory) return; // 로그아웃된 뒤 늦게 온 응답으로 세션을 새로 만들지 않는다
    memory = { ...memory, user };
    write(memory);
    notify();
  },
  /** 세션을 지운다 (예: 로그아웃·탈퇴 때 api/customer.js, 토큰이 끝내 401 로 거부될 때 http.js). 저장소·예약 초안도 함께 지운다 */
  clear() {
    generation++; // 로그아웃도 로그인 경계 — 진행 중이던 요청의 늦은 응답이 세션을 되살리지 못하게
    memory = null;
    write(null);
    clearSessionDrafts(); // 예약 초안(주소·요청사항)은 bookingDraft 모듈이 로드되지 않은 실행에서도 지운다 (외부 검토 3차)
    notify();
  },
  /** 세션이 바뀌면(로그인·삭제·사용자 갱신) 알린다 — RequireAuth 가 useSyncExternalStore 로 구독해 401 뒤 바로 로그인 화면으로 간다 */
  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  // 아래 셋은 지금 세션의 값만 읽는다. 로그아웃 상태면 null
  accessToken: () => memory?.accessToken ?? null,
  refreshToken: () => memory?.refreshToken ?? null,
  user: () => memory?.user ?? null,
  /** 가입까지 끝난 로그인 상태 (RequireAuth 가 본다). 가입 도중이면 false — signupStep 으로 이어간다 */
  isSignedIn: () => Boolean(memory?.accessToken && memory?.user?.profileComplete),
  /** 토큰은 있지만 가입이 덜 끝난 상태의 다음 화면 키 ('address' | 'housing' | 'complete') */
  signupStep: () => (memory?.accessToken ? (memory.user?.signupStep ?? null) : null),
};
