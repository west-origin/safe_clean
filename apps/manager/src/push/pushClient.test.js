// pushClient 테스트: 기기 토큰을 받아 로그인한 사용자에게만 서버 등록하는지, 로그아웃이 서버에서 지우는지,
// 로그아웃·401 강제 로그아웃 뒤 기기의 푸시 등록을 풀고 다음 로그인 때 새 토큰을 받는지(토큰이 오기 전에 강제 로그아웃된 콜드 스타트,
// Android 에서 삭제가 끝나기 전에 다시 로그인해 옛 토큰이 오는 경우 포함),
// 권한 거부·중복 호출·웹에서는 아무것도 하지 않는지, 알림 탭이 data.type 에 맞는 경로로 가는지,
// 앱이 앞에 떠 있을 때 OS 배너를 띄우는 설정(capacitor.config.json)이 있는지 본다.
// 실제 Capacitor 플러그인·서버 없이 대역 플러그인(fakePlugin)과 가짜 서버(test/fakeHttp.js)로 돌린다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { initPush, pushTarget, __resetPush, STALE_TOKEN_RETRY_MS } from './pushClient.js';
import { deviceToken } from './deviceToken.js';
import { loginWithPhone, logout } from '../api/manager.js';
import { session } from '../api/session.js';
import { __fake } from '../api/http.js';

// api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다. vi.mock 은 위 import 보다 먼저 적용된다.
// 보낸 요청은 __fake.calls, 등록된 푸시 토큰은 __fake.pushTokens 에 남는다.
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

/**
 * @capacitor/push-notifications 대역: 권한을 주고 register() 때 'registration' 을 발생시킨다.
 * unregister() 뒤의 register() 는 다음 토큰을 준다 — 실제 Android(FCM)도 등록을 풀면 옛 토큰이 무효가 되고 새 토큰을 받는다.
 * holdFirstRegistration: 첫 register() 의 'registration' 을 바로 내지 않고 releaseRegistration() 때 낸다 —
 *   Android FCM 이 오래된 토큰을 새로 받으러 서버에 다녀오는 동안(콜드 스타트) 다른 일이 먼저 끝나는 순서를 흉내 낸다.
 * slowDelete: Android 플러그인처럼 unregister() 가 FCM 토큰 삭제를 기다리지 않고 바로 끝난다. finishDelete() 전까지
 *   register() 는 아직 지워지지 않은 옛 토큰을 준다
 */
function fakePlugin({ permission = 'granted', tokens = ['device-token-1', 'device-token-2'], holdFirstRegistration = false, slowDelete = false } = {}) {
  const listeners = {}; // 이벤트 이름 → 등록된 함수. 테스트가 직접 꺼내 불러 알림 탭 등을 흉내 낸다
  let unregistered = 0; // 삭제가 끝난 unregister() 횟수 = 다음 register() 가 줄 토큰의 순번
  let pendingDeletes = 0; // slowDelete: 불렸지만 아직 끝나지 않은 삭제 수
  let held = holdFirstRegistration ? null : undefined; // null = 첫 register 를 기다리는 중, 함수 = 보류한 'registration', undefined = 보류 안 함
  return {
    listeners,
    addListener: vi.fn(async (name, fn) => { listeners[name] = fn; }),
    // 늘 '아직 묻지 않음' 으로 답해 initPush 가 requestPermissions 를 부르게 한다. 그 답이 permission 옵션이다
    checkPermissions: vi.fn(async () => ({ receive: 'prompt' })),
    requestPermissions: vi.fn(async () => ({ receive: permission })),
    register: vi.fn(async () => {
      const value = tokens[Math.min(unregistered, tokens.length - 1)];
      if (held === null) held = () => listeners.registration?.({ value });
      else listeners.registration?.({ value });
    }),
    unregister: vi.fn(async () => {
      if (slowDelete) pendingDeletes += 1;
      else unregistered += 1;
    }),
    releaseRegistration: () => held?.(), // 보류한 첫 'registration' 을 이제 낸다
    finishDelete: () => { unregistered += pendingDeletes; pendingDeletes = 0; }, // slowDelete: FCM 삭제가 이제 끝났다
  };
}

describe('pushClient — 기기 토큰 등록', () => {
  // 테스트마다 저장소·세션·가짜 서버 기록·pushClient 모듈 상태를 비우고 시작한다
  beforeEach(() => {
    localStorage.clear();
    session.clear();
    __fake.calls.length = 0;
    __fake.pushTokens.length = 0;
    __resetPush();
  });

  it('권한을 받으면 등록하고, 로그인돼 있을 때만 서버에 토큰을 보낸다. 로그인하면 그때 보내고 로그아웃은 서버에서 지운다', async () => {
    const plugin = fakePlugin();
    expect(await initPush({ plugin, platform: 'ios' })).toBe(true);
    expect(deviceToken.get()).toBe('device-token-1');
    expect(__fake.pushTokens).toEqual([]); // 아직 로그인 전

    // 로그인 → 세션 구독이 syncToken 을 깨워 PUT /push/token 을 보낸다
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await Promise.resolve(); // 비동기 등록이 진행되도록 한 번 양보한다
    expect(__fake.pushTokens).toEqual([{ platform: 'ios', token: 'device-token-1' }]);

    // 의도한 로그아웃: 서버 행을 먼저 지우고(DELETE), 세션이 지워지면 강제 로그아웃과 같은 길로 기기 등록도 푼다
    await logout();
    expect(__fake.calls.some((c) => c.method === 'DELETE' && c.path === '/push/token' && c.body.token === 'device-token-1')).toBe(true);
    expect(__fake.pushTokens).toEqual([]);
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));
    expect(deviceToken.get()).toBeNull();
  });

  it('401 로 세션이 지워지면(강제 로그아웃) 기기의 푸시 등록을 풀고, 다음 로그인 때 새 토큰을 받아 등록한다 (문제 목록 17번)', async () => {
    const plugin = fakePlugin();
    await initPush({ plugin, platform: 'android' });
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'android', token: 'device-token-1' }]));

    // 강제 로그아웃: 토큰과 refresh 가 모두 401 이면 api/http.js 는 session.clear() 만 부른다(http.test.js 참고) — 그대로 흉내 낸다.
    // 서버 행 삭제(DELETE /push/token)는 로그인이 필요해 보낼 수 없으므로 보내지 않고, 기기 쪽 등록만 푼다
    session.clear();
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));
    expect(deviceToken.get()).toBeNull();
    expect(__fake.calls.some((c) => c.method === 'DELETE' && c.path === '/push/token')).toBe(false);

    // 다음 로그인: register() 를 다시 불러 새 토큰을 받아 그 사용자에게 등록한다. 옛 토큰 행은 서버가 다음 발송 때 무효 토큰으로 지운다
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toContainEqual({ platform: 'android', token: 'device-token-2' }));
    expect(plugin.register).toHaveBeenCalledTimes(2);
  });

  // 콜드 스타트: 저장된 세션으로 앱을 열었는데 기기 토큰('registration')이 오기 전에 401 강제 로그아웃이 먼저 끝난 경우.
  // 그때는 풀 토큰이 아직 없지만, 늦게 온 토큰은 앞 사용자의 서버 행을 가리킬 수 있어 저장하지 않고 등록을 풀어야 한다 (합친 38건 검토 R12)
  it('토큰이 오기 전에 강제 로그아웃되면, 늦게 온 토큰은 저장하지 않고 등록을 풀며 다음 로그인 때 새 토큰을 받는다', async () => {
    await loginWithPhone({ phone: '12345678', password: 'password1' }); // 앞 실행에서 저장된 세션 (서버에는 이 사용자·토큰 행이 남아 있다)
    const plugin = fakePlugin({ holdFirstRegistration: true });
    await initPush({ plugin, platform: 'android' });
    expect(deviceToken.get()).toBeNull(); // 아직 토큰이 오지 않았다

    session.clear(); // refreshMe 의 /auth/me·/auth/refresh 가 401 → api/http.js 가 세션만 지운다
    plugin.releaseRegistration(); // 이제야 FCM 이 토큰을 돌려준다
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));
    expect(deviceToken.get()).toBeNull(); // 로그아웃된 기기에 앞 사용자의 토큰을 남기지 않는다
    expect(__fake.pushTokens).toEqual([]);

    // 다음 로그인: register() 를 다시 불러 새 토큰을 받아 등록한다
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'android', token: 'device-token-2' }]));
    expect(plugin.register).toHaveBeenCalledTimes(2);
  });

  // Android 플러그인의 unregister 는 FCM 토큰 삭제를 기다리지 않고 끝난다. 곧바로 다시 로그인하면 register() 가 아직 지워지지 않은
  // 옛 토큰을 돌려주는데, 그대로 등록하면 곧 무효가 돼 새 사용자는 앱을 다시 켤 때까지 푸시를 못 받았다 (합친 38건 검토 R13).
  // 방금 푼 토큰이 오면 서버에 등록하지 않고 잠시 뒤 register() 를 한 번 더 부른다
  it('Android: 로그아웃 직후 다시 로그인해 방금 푼 토큰이 오면 등록하지 않고 잠시 뒤 새 토큰을 받아 등록한다', async () => {
    const plugin = fakePlugin({ slowDelete: true });
    await initPush({ plugin, platform: 'android' });
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'android', token: 'device-token-1' }]));
    await logout(); // 서버 행 삭제(DELETE) + unregister — 삭제는 아직 FCM 에서 진행 중
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));

    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    try {
      await loginWithPhone({ phone: '12345678', password: 'password1' }); // 곧바로 다시 로그인 → register() 가 옛 토큰 device-token-1 을 준다
      await vi.advanceTimersByTimeAsync(0);
      expect(plugin.register).toHaveBeenCalledTimes(2);
      expect(__fake.pushTokens).toEqual([]); // 곧 무효가 될 옛 토큰은 등록하지 않는다
      expect(deviceToken.get()).toBeNull();

      plugin.finishDelete(); // FCM 삭제가 끝났다
      await vi.advanceTimersByTimeAsync(STALE_TOKEN_RETRY_MS); // 잠시 뒤 한 번 더 register()
      expect(plugin.register).toHaveBeenCalledTimes(3);
      expect(__fake.pushTokens).toEqual([{ platform: 'android', token: 'device-token-2' }]);
    } finally {
      vi.useRealTimers();
    }
  });

  // iOS 는 등록을 풀었다 다시 해도 같은 토큰이 오는 것이 정상이라(삭제를 기다리는 문제도 없다) 기다리지 않고 바로 등록한다
  it('iOS: 다시 로그인해 같은 토큰이 오면 바로 등록한다', async () => {
    const plugin = fakePlugin({ slowDelete: true });
    await initPush({ plugin, platform: 'ios' });
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'ios', token: 'device-token-1' }]));
    await logout();
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'ios', token: 'device-token-1' }]));
    expect(plugin.register).toHaveBeenCalledTimes(2);
  });

  it('권한을 거부하면 등록하지 않고, 두 번 부르면 무시하며, 웹에서는 아무것도 하지 않는다', async () => {
    const plugin = fakePlugin({ permission: 'denied' });
    expect(await initPush({ plugin, platform: 'android' })).toBe(false);
    expect(plugin.register).not.toHaveBeenCalled();
    expect(await initPush({ plugin, platform: 'android' })).toBe(false); // 이미 시작됨
    __resetPush(); // started 를 풀어, 대역 없이 부르는 웹 경로를 따로 확인한다
    expect(await initPush()).toBe(false); // jsdom = 웹
  });

  it('알림을 탭하면 data.type 에 맞는 화면으로 간다', async () => {
    const onOpen = vi.fn();
    const plugin = fakePlugin();
    await initPush({ plugin, platform: 'ios', onOpen });
    // 플러그인이 보냈을 '알림 탭' 이벤트를 직접 발생시킨다 → onOpen 이 data 를 그대로 받는다
    plugin.listeners.pushNotificationActionPerformed({ notification: { data: { type: 'job', bookingId: 'b1' } } });
    expect(onOpen).toHaveBeenCalledWith({ type: 'job', bookingId: 'b1' });
    expect(pushTarget({ type: 'job', bookingId: 'b1' })).toBe('/job/b1');
    expect(pushTarget({ type: 'call' })).toBe('/call');
    expect(pushTarget({ type: 'penalty' })).toBe('/profile/penalties'); // 자체 점검 #28
    expect(pushTarget({ type: 'settlement' })).toBe('/settlement');
    expect(pushTarget({})).toBe('/');
  });
});

describe('앱이 앞에 떠 있을 때의 푸시 (자체 점검 후속 재검토)', () => {
  it('OS 배너를 띄우도록 설정돼 있다 — 없으면 일정·업무 상세를 보는 중에 온 취소·일정 변경 알림이 보이지 않는다', async () => {
    // 앱이 앞에 떠 있을 때 OS 배너를 띄울지는 코드가 아니라 capacitor.config.json 설정이 정한다 — 그 값을 직접 읽는다
    const config = (await import('../../capacitor.config.json')).default;
    expect(config.plugins.PushNotifications.presentationOptions).toEqual(expect.arrayContaining(['banner', 'list', 'alert']));
  });
});
