// 푸시 등록 (BACKEND_PLAN.md B6). 네이티브 앱(Capacitor)에서만 동작한다 — 웹(개발 서버)에서는 아무것도 하지 않는다.
// 흐름: 앱 시작 → initPush() → 권한 요청 → PushNotifications.register() → 'registration' 이벤트로 기기 토큰 → 로그인돼 있으면 PUT /push/token.
// 로그인 상태가 바뀌면(세션 구독) 다시 등록한다. 로그아웃은 api.logout 이 deviceToken 을 읽어 DELETE /push/token 을 먼저 보낸다.
// 로그인 → 로그아웃 전환이면, 의도한 로그아웃이든 토큰이 거부돼(401) api/http.js 가 세션만 지운 강제 로그아웃이든 같은 길(forgetDevice)로
// 이 기기의 푸시 등록을 풀고(unregister) 기기 토큰을 버린다. 강제 로그아웃은 로그인이 필요한 DELETE 를 보낼 수 없어 서버에 토큰 행이 남는데,
// 등록을 풀지 않으면 이 기기에서 다시 로그인할 때까지 앞 사용자의 푸시가 로그아웃된 기기로 왔다 (문제 목록 17번).
// 남은 서버 행은 다음 발송 때 FCM 이 무효 토큰으로 답하면 서버(PushService.send)가 지운다. 다음 로그인 때는 register() 를 다시 불러 새 토큰을 받는다.
// 로그아웃 때 기기 토큰이 아직 오지 않았으면(콜드 스타트에 401 이 먼저 끝남) 늦게 온 토큰을 저장하지 않고 그때 등록을 푼다 (합친 38건 검토 R12).
// Android 는 등록 해제가 FCM 토큰 삭제를 기다리지 않아, 곧바로 다시 로그인해 방금 푼 토큰이 오면 등록하지 않고 잠시 뒤 다시 받는다 (합친 38건 검토 R13).
// 화면 이동(알림 탭)은 data.type/bookingId 로 — 라우터를 여기서 모르므로 onOpen 콜백으로 넘긴다.
// 예: App.jsx 가 시작 때 initPush 를 부르고 알림을 탭하면 router.navigate(pushTarget(data)) 로 옮긴다.
// 설정 화면은 getPushState·subscribePushState 로 OS 권한 상태를 보여 준다.
import { Capacitor } from '@capacitor/core';
import { deviceToken } from './deviceToken.js';
import { registerPushToken, subscribeSession, isSignedIn } from '../api/customer.js';
import { session } from '../api/session.js';

let started = false; // initPush 가 이미 시작했는가 — 리스너를 두 번 달지 않게
/** OS 권한 상태 — 설정 화면이 '켜져 있는데 안 온다' 를 설명하는 데 쓴다 (검증 검토). unknown = 아직 모름·웹, granted, denied, error(등록 실패) */
let permissionState = 'unknown';
const stateListeners = new Set();
/** 권한 상태를 바꾸고 구독자(설정 화면)에게 알린다 */
function setPermissionState(next) {
  permissionState = next;
  stateListeners.forEach((fn) => fn());
}
export const getPushState = () => permissionState; // 설정 화면이 useSyncExternalStore 로 읽는다
/** 권한 상태 구독. 반환값은 해제 함수 */
export function subscribePushState(fn) {
  stateListeners.add(fn);
  return () => stateListeners.delete(fn);
}

/** 서버 등록 — 토큰이 있고 로그인돼 있을 때만. 같은 사용자·같은 토큰은 한 번만(토큰 refresh·setUser 마다 세션 구독이 깨워도 반복하지 않게, 검증 검토). 실패하면 다음 기회에 */
let registered = null; // `${userId}:${token}`
async function syncToken() {
  const token = deviceToken.get();
  if (!isSignedIn()) registered = null; // 로그아웃(서버 행 삭제)됐다 — 같은 사람이 다시 로그인하면 다시 등록해야 한다 (검수 4차: 키가 남아 재로그인 뒤 푸시가 끊겼다)
  if (!token || !isSignedIn() || deviceToken.suspended()) return; // 로그아웃 진행 중이면 등록하지 않는다
  const key = `${session.user()?.id ?? ''}:${token}`;
  if (registered === key) return;
  try {
    await registerPushToken({ platform: deviceToken.platform(), token });
    registered = key;
  } catch {
    // 서버가 죽어 있어도 앱은 계속 — 다음 로그인·시작 때 다시 등록한다
  }
}

let pushPlugin = null; // start() 가 쓰는 플러그인(테스트는 대역). 로그아웃 때 등록을 풀고 다음 로그인 때 다시 등록하는 데 쓴다
let wasSignedIn = false; // 세션 구독이 마지막으로 본 로그인 상태 — 로그인 ↔ 로그아웃 전환을 알아보려고 적어 둔다
let needsNewToken = false; // 로그아웃 때 등록을 풀었다 → 다음 로그인 때 register() 로 새 토큰을 받아야 한다
let forgetOnRegistration = false; // 로그아웃 때 풀 토큰이 아직 없었다(발급 대기 중) → 로그아웃된 채로 늦게 온 'registration' 은 저장하지 않고 등록을 푼다. 로그인하면 끈다
// 진행 중인 등록 해제. 다시 등록은 이것이 끝난 뒤에 한다 — 늦게 끝난 해제가 새 등록을 풀지 않게.
// 주의: Android 플러그인의 unregister 는 FCM 토큰 삭제(deleteToken)를 기다리지 않고 바로 끝나서, 이것이 끝나도 삭제가 끝났다는 보장은 없다 —
// 그 틈은 아래 lastUnregisteredToken 이 막는다 (합친 38건 검토 R13)
let unregistering = Promise.resolve();
/**
 * Android: 방금 등록을 푼 토큰이 다시 오면, 그 토큰을 등록하지 않고 register() 를 한 번 더 부르기까지 기다리는 시간 (합친 38건 검토 R13).
 * 한계: 이 시간 안에 FCM 삭제가 끝나지 않았거나 삭제 자체가 실패했으면, 두 번째 답(옛 토큰일 수 있다)을 그대로 등록한다.
 * 삭제가 실패했으면 그 토큰은 여전히 유효하고, 늦게 끝났으면 다음 발송 때 서버가 무효 토큰 행을 지우며 앱을 다시 켤 때(start 의 register) 새 토큰을 받는다.
 * 삭제 완료를 앱이 알 수 있게 하려면 네이티브 플러그인을 고쳐야 하는데, 네이티브 코드는 고치지 않는다 (CLAUDE.md §8 단계 6)
 */
export const STALE_TOKEN_RETRY_MS = 5000;
let lastUnregisteredToken = null; // 마지막으로 등록을 푼 토큰 — Android 에서 삭제가 끝나기 전의 register() 가 이 값을 그대로 돌려줄 수 있다
let staleRetried = false; // 그 토큰이 와서 register() 를 이미 한 번 더 불렀다 — 또 오면 받아들인다(위 한계)
let staleRetryTimer = null; // 한 번 더 부를 register() 타이머
let stopSessionWatch = null; // 세션 구독 해제 함수 (테스트의 __resetPush 가 쓴다)

/**
 * 세션이 바뀔 때마다(로그인·토큰 갱신·사용자 갱신·로그아웃) 불린다.
 * 로그인 → 로그아웃이면 forgetDevice 로 기기 등록을 풀고, 로그아웃 → 로그인인데 전에 등록을 풀었으면 register() 로 새 토큰을 받는다
 * (새 토큰은 'registration' 이벤트로 와 syncToken 이 서버에 등록한다). 그 밖에는 syncToken 으로 서버 등록만 맞춘다
 */
async function onSessionChange() {
  const signedIn = isSignedIn();
  const signedOut = wasSignedIn && !signedIn;
  const signedBackIn = !wasSignedIn && signedIn;
  wasSignedIn = signedIn;
  if (signedOut) return forgetDevice();
  if (signedBackIn) forgetOnRegistration = false; // 로그인했다 — 이제 오는 토큰은 이 사용자에게 등록한다 (서버가 같은 토큰의 다른 사용자 행을 지운다)
  if (signedBackIn && needsNewToken && pushPlugin && permissionState === 'granted') {
    needsNewToken = false;
    await unregistering;
    await pushPlugin.register().catch(() => {
      needsNewToken = true; // 발급 요청이 실패했다 — 다음 로그인 때 다시 받는다
    });
    return;
  }
  return syncToken();
}

/**
 * 로그인 → 로그아웃 전환 때 이 기기의 푸시 등록을 푼다. 의도한 로그아웃(api.logout 이 서버 행을 먼저 지운 뒤 세션을 지움)과
 * 401 강제 로그아웃(api/http.js 가 세션만 지움, 서버 행은 남음)이 같이 지나는 길이다.
 * 기기 토큰과 중복 방지 키를 버린다 — 풀린 토큰을 다음 로그인에 다시 쓰지 않게. 해제가 실패해도 로그아웃은 그대로 진행된다
 * (그러면 앞 사용자의 서버 행이 남은 토큰으로 푸시가 올 수 있다 — 다음 사람이 같은 토큰을 등록하면 서버가 그 행을 지운다)
 */
function forgetDevice() {
  registered = null;
  const token = deviceToken.get(); // 풀 토큰 (없으면 null)
  const hadToken = Boolean(token);
  deviceToken.set(null, deviceToken.platform());
  if (!pushPlugin) return; // 웹 — 풀 것이 없다
  if (!hadToken) {
    // 토큰을 아직 받지 못했다(발급 대기 중, 또는 권한 없음·발급 실패). 콜드 스타트에 Android FCM 이 오래된 토큰을 새로 받으러 다녀오는 사이
    // 401 강제 로그아웃이 먼저 끝나면 이렇게 된다. 곧 올 'registration' 은 방금 로그아웃된 사용자의 서버 행을 가리킬 수 있으므로,
    // 표시만 남기고 그 토큰이 오면 저장하지 않고 등록을 푼다('registration' 리스너). 예전에는 여기서 끝나 그 토큰이 저장된 채 풀리지 않고,
    // 다시 로그인할 때까지 앞 사용자의 알림이 로그아웃된 기기로 왔다 (합친 38건 검토 R12)
    forgetOnRegistration = true;
    return;
  }
  needsNewToken = true;
  rememberUnregistered(token);
  unregistering = pushPlugin.unregister().catch(() => {});
  return unregistering;
}

/** 등록을 푸는 토큰을 적어 둔다 — 다음 로그인 뒤 같은 토큰이 오면 한 번은 등록하지 않는다('registration' 리스너, 합친 38건 검토 R13) */
function rememberUnregistered(token) {
  lastUnregisteredToken = token;
  staleRetried = false;
}

/**
 * @param {object} [opts]
 * @param {(data: Record<string, string>) => void} [opts.onOpen] 사용자가 알림을 탭했을 때 (data: { type, bookingId })
 * @param {typeof import('@capacitor/push-notifications').PushNotifications} [opts.plugin] 테스트용 대역
 * @param {string} [opts.platform] 테스트용 (기본 Capacitor.getPlatform())
 * 푸시를 시작한다 (App.jsx 가 앱 시작 때 부른다). 권한을 받고 register() 까지 했으면 true,
 * 웹·이미 시작함·권한 거부·예외면 false. 예외일 때만 started 를 되돌려 다음 호출이 다시 시도한다 (거부는 그대로).
 */
export async function initPush({ onOpen, plugin, platform } = {}) {
  if (started) return false;
  const native = plugin ? true : Capacitor.isNativePlatform(); // 테스트가 plugin 대역을 넘기면 네이티브로 친다
  if (!native) return false; // 웹은 푸시 없음
  started = true;
  try {
    return await start({ onOpen, plugin, platform });
  } catch {
    started = false; // 플러그인 로드·권한 요청이 실패하면 다음 initPush 가 다시 시도할 수 있게 (검수 3차)
    // 주의: 리스너를 붙인 뒤(권한 확인·register 단계)에 실패했으면 붙은 리스너·세션 구독을 떼지 않아, 다시 시도하면 한 번 더 붙는다
    return false;
  }
}

/** initPush 의 실제 작업: 플러그인 리스너 → 세션 구독 → 권한 확인·요청 → register(). 토큰은 'registration' 이벤트로 온다 */
async function start({ onOpen, plugin, platform }) {
  const push = plugin ?? (await import('@capacitor/push-notifications')).PushNotifications; // 네이티브에서만 플러그인을 불러온다 (동적 import)
  pushPlugin = push; // 로그아웃 때 등록 해제(unregister)·다음 로그인 때 다시 등록(register)에 쓴다
  const os = platform ?? Capacitor.getPlatform(); // ios | android

  // OS 가 기기 토큰을 발급했다 → 저장하고, 로그인돼 있으면 서버에 등록한다
  await push.addListener('registration', (t) => {
    if (forgetOnRegistration && !isSignedIn()) {
      // 토큰보다 로그아웃이 먼저 끝났다(forgetDevice 참고). 이 토큰은 앞 사용자의 서버 행을 가리킬 수 있어 저장하지 않고
      // forgetDevice 와 같이 등록을 푼다 — 다음 로그인 때 register() 로 새 토큰을 받는다 (합친 38건 검토 R12)
      forgetOnRegistration = false;
      needsNewToken = true;
      rememberUnregistered(t.value);
      unregistering = push.unregister().catch(() => {});
      return;
    }
    if (os === 'android' && t.value === lastUnregisteredToken && !staleRetried) {
      // 방금 등록을 푼 토큰이 다시 왔다. Android 플러그인의 unregister 는 FCM 삭제를 기다리지 않아, 삭제가 끝나기 전의 register() 는
      // 곧 무효가 될 옛 토큰을 돌려준다 — 그대로 등록하면 새로 로그인한 사용자는 앱을 다시 켤 때까지 푸시를 받지 못했다.
      // 서버에 등록하지 않고 잠시 뒤 register() 를 한 번 더 부른다(새 토큰은 이 리스너로 다시 온다). iOS 는 같은 토큰이 오는 것이 정상이라 보지 않는다
      staleRetried = true;
      clearTimeout(staleRetryTimer);
      staleRetryTimer = setTimeout(() => {
        staleRetryTimer = null;
        push.register().catch(() => {
          needsNewToken = true; // 발급 요청이 실패했다 — 다음 로그인 때 다시 받는다
        });
      }, STALE_TOKEN_RETRY_MS);
      return;
    }
    lastUnregisteredToken = null; // 새 토큰(또는 한 번 기다린 뒤의 답)을 받아들인다
    deviceToken.set(t.value, os);
    syncToken();
  });
  await push.addListener('registrationError', () => {
    // 권한 거부는 register() 를 부르기 전에 'denied' 로 끝나므로 이 이벤트로 오지 않는다
    deviceToken.set(null, os); // 기기 토큰 발급 실패(iOS APNs·Android FCM) — 푸시 없이 진행
    setPermissionState('error');
  });
  await push.addListener('pushNotificationReceived', (n) => {
    // 앱이 화면에 떠 있을 때 온 알림 — 화면이 바로 새로 읽게 알린다 (외부 검토 3차).
    // OS 배너는 capacitor.config.json 의 PushNotifications.presentationOptions 가 띄운다 — 예전에는 설정이 없어 앞에 떠 있으면 아무것도 안 보였고,
    // 카드에 흔적이 없는 '매니저가 늦어지고 있어요' 는 통째로 사라졌다 (자체 점검 후속 검토)
    notifyPushReceived(n?.data ?? {});
  });
  // 사용자가 알림을 탭해 앱이 열렸다 — onOpen(App.jsx)이 pushTarget 으로 화면을 옮긴다
  await push.addListener('pushNotificationActionPerformed', (action) => {
    if (onOpen) onOpen(action?.notification?.data ?? {});
  });
  // 로그인하면 이미 받은 기기 토큰을 그 사용자에게 붙이고, 로그아웃하면 기기 등록을 푼다 (onSessionChange).
  // 전환을 알아보려면 구독 전의 로그인 상태부터 적어 둔다 (앱 시작 때 저장된 세션이 이미 있으면 true)
  wasSignedIn = isSignedIn();
  stopSessionWatch = subscribeSession(() => onSessionChange());

  // 아직 묻지 않았으면(prompt) OS 권한 창을 띄운다. 이미 거부됐으면 다시 묻지 않고 denied 로 끝낸다
  let permission = await push.checkPermissions();
  if (permission.receive === 'prompt' || permission.receive === 'prompt-with-rationale') permission = await push.requestPermissions();
  if (permission.receive !== 'granted') {
    setPermissionState('denied');
    return false;
  }
  setPermissionState('granted');
  await push.register();
  return true;
}

/** 푸시 data.type → 화면 (backend push/PushHandler 의 type 값과 짝) */
export function pushTarget(data = {}) {
  switch (data.type) {
    case 'live': return `/service/live?booking=${data.bookingId}`; // 푸시를 탭하면 바로 영상
    case 'recording': return `/service/live?booking=${data.bookingId}&replay=1`;
    case 'inquiry': return '/settings/inquiry'; // 문의 답변 — 화면 위쪽 '내 문의'
    case 'reservation':
    case 'series': return '/reservations';
    default: return '/home';
  }
}

const PUSH_EVENT = 'ansimclean:push'; // window 사용자 정의 이벤트 이름
/** 앱이 떠 있을 때 푸시가 오면 화면에 알린다 (window 이벤트 — 라우터·화면을 여기서 모르므로) */
function notifyPushReceived(data) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(PUSH_EVENT, { detail: data }));
}
/** 화면이 구독: 푸시가 오면 fn(data). 반환값은 해제 함수. 예: 예약 목록(Reservations)이 usePolling 의 subscribe 로 넘겨, 푸시가 오면 목록을 바로 다시 받는다 */
export function onPushReceived(fn) {
  if (typeof window === 'undefined') return () => {};
  const handler = (e) => fn(e.detail ?? {});
  window.addEventListener(PUSH_EVENT, handler);
  return () => window.removeEventListener(PUSH_EVENT, handler);
}

/** 테스트용 */
export function __resetPush() {
  started = false;
  registered = null;
  deviceToken.set(null, null);
  permissionState = 'unknown';
  stopSessionWatch?.(); // 앞 테스트의 initPush 가 붙인 세션 구독을 뗀다 — 남아 있으면 같은 세션 알림에 두 번 등록했다
  stopSessionWatch = null;
  pushPlugin = null;
  wasSignedIn = false;
  needsNewToken = false;
  forgetOnRegistration = false;
  unregistering = Promise.resolve();
  clearTimeout(staleRetryTimer);
  staleRetryTimer = null;
  lastUnregisteredToken = null;
  staleRetried = false;
}
