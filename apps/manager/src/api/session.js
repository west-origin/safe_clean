// 로그인 세션 보관 (토큰 + 사용자). 화면은 이 파일을 직접 쓰지 않는다 — api/manager.js 를 거친다 (예: isSignedIn()·signupStep()).
// 네이티브 앱(Capacitor)은 capacitor-secure-storage-plugin(iOS Keychain / Android Keystore 로 암호화한 SharedPreferences)에, 웹 개발 서버는 localStorage 에 둔다 (BACKEND_PLAN.md 결정 10, B7 전).
// WKWebView 의 localStorage 는 저장 공간 압박 때 지워질 수 있어 사용자가 이유 없이 로그아웃됐다 (완결성 검토). 평문 UserDefaults/SharedPreferences 는 탈옥·루팅 기기와 iOS 백업에서 읽혀
// Keychain/Keystore 로 옮겼다 (보안 검토 2026-09-22). 네이티브는 **보안 저장소에만** 둔다 — localStorage 평문 사본·삭제 실패 뒤 옛 세션 복원을 없앴다 (감사 M-01).
// 읽기는 비동기라 App 이 hydrate() 를 기다린 뒤 라우터를 그린다.
// 세션이 바뀌면 subscribe 한 곳에 알린다 (예: RequireAuth 는 로그인 화면으로, live/liveClient.js 는 폴링을 멈춘다).
import { Capacitor } from '@capacitor/core';

const NATIVE = Capacitor.isNativePlatform(); // iOS·Android 앱으로 실행 중이면 true, 브라우저(개발 서버)면 false
let secure = null; // capacitor-secure-storage-plugin 의 SecureStoragePlugin (네이티브에서만 로드)
const KEY = 'ansimclean.manager.auth';

/** localStorage 의 세션을 읽는다. 없거나 JSON 이 깨졌으면 null. 웹은 여기가 저장소이고, 네이티브는 예전 평문 세션을 옮길 때만 읽는다 */
function read() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) ?? null;
  } catch {
    return null;
  }
}

// 네이티브 쓰기가 끝났는지만 적는 표식(비밀 아님). 저장·삭제가 실패하거나 도중에 앱이 죽으면 남아, 다음 실행에서 옛 Keychain 세션을 읽지 않는다(로그인부터 다시)
const DIRTY = `${KEY}.pending`;
// storageWrites = 네이티브 저장 작업을 한 줄로 세우는 Promise 사슬. 앞 작업이 끝나야 다음이 시작돼 로그인 저장과 로그아웃 삭제의 순서가 뒤바뀌지 않는다
let storageWrites = Promise.resolve();
// revision = write 할 때마다 +1. 늦게 끝난 옛 쓰기가 pending 표식을 지우거나, hydrate 가 읽은 옛 값으로 새 세션을 덮지 않게 비교한다
let revision = 0;
let generation = 0; // 로그인·로그아웃 경계. 같은 계정의 토큰 갱신은 바꾸지 않는다.

/** pending 표식을 켜거나(true) 끈다(false). localStorage 가 막혀 있으면 조용히 넘어간다 */
function markPending(pending) {
  try {
    if (pending) localStorage.setItem(DIRTY, '1');
    else localStorage.removeItem(DIRTY);
  } catch {
    // 저장소가 막힌 실행에서는 메모리 세션만 사용할 수 있다.
  }
}

/**
 * 보안 저장소에 세션을 쓰거나(value 있음) 지운다(null). storageWrites 줄 맨 뒤에 붙여 순서대로 실행한다.
 * current(이 쓰기의 revision)가 아직 마지막 쓰기일 때만 pending 표식을 지운다 — 뒤에 새 쓰기가 있으면 그 쓰기가 지운다.
 * 돌려주는 Promise 는 저장이 실패하면 reject 된다 (hydrate 의 평문 세션 이전이 이것을 기다린다)
 */
function persistNative(value, current) {
  // 앞 작업이 실패했어도(.catch) 이 쓰기는 실행한다
  const pendingWrite = storageWrites.catch(() => {}).then(async () => {
    if (value) await secure.set({ key: KEY, value: JSON.stringify(value) });
    else await secure.remove({ key: KEY });
    if (current === revision) markPending(false);
  });
  storageWrites = pendingWrite.catch(() => {}); // 실패하면 pending 표식을 남긴다.
  return pendingWrite;
}

/**
 * 세션을 저장한다(null 이면 삭제). 네이티브: revision 을 올리고 pending 표식을 켠 뒤 평문 사본을 지우고
 * 보안 저장소 쓰기를 줄에 넣는다. 웹: localStorage 에 바로 쓴다.
 */
function write(value) {
  if (NATIVE) {
    const current = ++revision;
    markPending(true);
    try { localStorage.removeItem(KEY); } catch { /* plaintext fallback 금지 */ }
    // 플러그인(secure)은 hydrate() 가 불러온다. 그 전에 쓰면 저장하지 않고 pending 표식만 남아 다음 실행은 로그인부터 다시 한다
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

// memory = 지금 세션(메모리 사본). 웹은 이 모듈이 처음 로드될 때 localStorage 에서 채우고, 네이티브는 hydrate() 가 채운다
let memory = NATIVE ? null : read();
let hydrated = !NATIVE; // 웹은 바로 위에서 localStorage 를 이미 읽었으므로 처음부터 끝난 상태
let hydrating = null; // 진행 중인 hydrate 의 Promise — 여러 번 불러도 한 번만 읽는다

/** 네이티브: 보안 저장소를 읽고 기존 평문 세션은 한 번만 이전한다. */
async function hydrate() {
  if (hydrated) return;
  if (hydrating) return hydrating;
  hydrating = (async () => {
    // 읽는 사이 로그인·로그아웃(write)이 일어나면 revision 이 바뀐다 — 그때는 읽은 값으로 메모리를 덮지 않는다
    const current = revision;
    // legacy = 보안 저장소로 옮기기 전 localStorage 에 평문으로 남아 있던 세션. 읽은 뒤 바로 지운다
    const legacy = read();
    // 지난 실행의 쓰기가 끝나지 않았으면(pending) 보안 저장소 값이 최신인지 알 수 없다 → 읽지 않고 로그인 전 상태로 시작한다.
    // localStorage 를 못 읽어도 같은 쪽(true)으로 둔다
    let pending = true;
    try { pending = localStorage.getItem(DIRTY) === '1'; } catch { /* fail closed */ }
    try { localStorage.removeItem(KEY); } catch { /* 평문을 새로 쓰지 않는다 */ }
    try {
      // 동적 import: 네이티브에서만 여기까지 오므로 웹 실행에서는 이 플러그인을 불러오지 않는다
      secure = (await import('capacitor-secure-storage-plugin')).SecureStoragePlugin;
      if (!pending && current === revision) {
        // 읽기가 실패하면 저장된 값이 없는 것으로 본다
        const value = await secure.get({ key: KEY }).then((r) => r.value, () => null);
        if (current === revision) {
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
    notify(); // 다 읽었으니 구독자에게 알린다
  })();
  return hydrating;
}
// subscribe() 로 등록된 함수들. 세션이 바뀔 때마다 모두 부른다
const listeners = new Set();
const notify = () => listeners.forEach((fn) => fn());

/** 앱이 쓰는 세션 창구. 읽기는 메모리 값(memory)만 보고, 바꿀 때는 저장(write)과 알림(notify)을 같이 한다 */
export const session = {
  /** 로그인·로그아웃 경계 번호. http.js·manager.js 가 요청 전에 적어 두고, 그 사이 바뀌었으면 늦은 응답을 버린다 */
  generation: () => generation,
  /** 앱 시작 때 App.jsx 가 한 번 기다린다 (네이티브만 실제로 읽는다) */
  hydrate,
  /** @returns {{ accessToken: string, refreshToken: string, expiresAt: number, user: object } | null} */
  get: () => memory,
  /** 서버의 로그인·가입 응답을 저장한다. 같은 로그인의 refresh는 renew를 사용한다. */
  set({ accessToken, refreshToken, expiresIn, user }) {
    generation++; // 새 로그인 = 새 경계. 옛 로그인의 늦은 refresh 응답은 renew 에서 거부된다
    // expiresIn 은 초 단위라 ms 시각(expiresAt)으로 바꿔 둔다. 응답에 user 가 없으면 있던 사용자 정보를 둔다
    memory = { accessToken, refreshToken, expiresAt: Date.now() + expiresIn * 1000, user: user ?? memory?.user ?? null };
    write(memory);
    notify();
  },
  /** 같은 로그인 세션의 refresh 응답만 반영한다. 로그아웃·새 로그인 경계를 넘으면 거부한다. */
  renew({ accessToken, refreshToken, expiresIn, user }, expectedGeneration) {
    if (generation !== expectedGeneration || !memory) return false;
    memory = { accessToken, refreshToken, expiresAt: Date.now() + expiresIn * 1000, user: user ?? memory.user };
    write(memory);
    notify();
    return true;
  },
  /** 사용자 정보만 갱신 (GET /auth/me, 가입 완료) */
  setUser(user) {
    if (!memory) return;
    memory = { ...memory, user };
    write(memory);
    notify();
  },
  /** 로그아웃·탈퇴·토큰 거부(http.js) 때 세션을 지운다. generation 을 올려 진행 중이던 요청·토큰 갱신이 되살리지 못하게 한다 */
  clear() {
    generation++;
    memory = null;
    write(null);
    notify();
  },
  /** 세션이 바뀌면(로그인·삭제·사용자 갱신) 알린다 — RequireAuth 가 useSyncExternalStore 로 구독해 401 뒤 바로 로그인 화면으로 간다 */
  subscribe(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },
  // 읽기용 지름길. 세션이 없으면 null
  accessToken: () => memory?.accessToken ?? null,
  refreshToken: () => memory?.refreshToken ?? null,
  user: () => memory?.user ?? null,
  /** 가입까지 끝난 로그인 상태 (RequireAuth 가 본다). 가입 도중이면 false — signupStep 으로 이어간다 */
  isSignedIn: () => Boolean(memory?.accessToken && memory?.user?.profileComplete),
  /** 토큰은 있지만 가입이 덜 끝난 상태의 다음 화면 키 ('address' | 'housing' | 'complete') */
  signupStep: () => (memory?.accessToken ? (memory.user?.signupStep ?? null) : null), // 매니저 계정에는 서버가 'complete'(또는 null)만 준다 (ProfileService.signupStep)
};
