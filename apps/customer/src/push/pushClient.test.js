// pushClient 테스트: 권한 → 기기 토큰 → 로그인 때 서버 등록(PUT /push/token)·로그아웃 때 삭제(DELETE),
// 로그아웃·401 강제 로그아웃 뒤 기기의 푸시 등록을 풀고 다음 로그인 때 새 토큰을 받는지(토큰이 오기 전에 강제 로그아웃된 콜드 스타트,
// Android 에서 삭제가 끝나기 전에 다시 로그인해 옛 토큰이 오는 경우 포함),
// 권한 거부·웹에서는 아무것도 안 함, 알림 탭 → 화면 경로(pushTarget), 앱이 앞에 있을 때의 배너 설정을 본다.
// 서버는 가짜(test/fakeHttp.js — __fake.pushTokens 가 서버에 등록된 토큰 목록), 푸시 플러그인은 아래 fakePlugin 대역이다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { initPush, getPushState, pushTarget, __resetPush, STALE_TOKEN_RETRY_MS } from './pushClient.js';
import { deviceToken } from './deviceToken.js';
import { loginWithPhone, logout } from '../api/customer.js';
import { session } from '../api/session.js';
import { __fake } from '../api/http.js';

// api/http.js 를 가짜 서버로 바꾼다 (vi.mock 은 파일 맨 위로 끌어올려져 위의 import 보다 먼저 적용된다)
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
  const listeners = {};
  let unregistered = 0; // 삭제가 끝난 unregister() 횟수 = 다음 register() 가 줄 토큰의 순번
  let pendingDeletes = 0; // slowDelete: 불렸지만 아직 끝나지 않은 삭제 수
  let held = holdFirstRegistration ? null : undefined; // null = 첫 register 를 기다리는 중, 함수 = 보류한 'registration', undefined = 보류 안 함
  return {
    listeners,
    addListener: vi.fn(async (name, fn) => { listeners[name] = fn; }),
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
  // 저장소·세션·가짜 서버 기록·pushClient 내부 상태(started·registered·권한)를 매번 비운다
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

    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await Promise.resolve(); // 로그인이 깨운 세션 구독 → syncToken(비동기)이 가짜 서버 요청을 마칠 틈을 준다
    expect(__fake.pushTokens).toEqual([{ platform: 'ios', token: 'device-token-1' }]);

    // 의도한 로그아웃: 서버 행을 먼저 지우고(DELETE), 세션이 지워지면 강제 로그아웃과 같은 길로 기기 등록도 푼다
    await logout();
    expect(__fake.calls.some((c) => c.method === 'DELETE' && c.path === '/push/token' && c.body.token === 'device-token-1')).toBe(true);
    expect(__fake.pushTokens).toEqual([]);
    await vi.waitFor(() => expect(plugin.unregister).toHaveBeenCalledTimes(1));

    // 같은 사람이 다시 로그인하면 다시 등록한다 (검수 4차: 중복 방지 키가 남아 앱을 껐다 켤 때까지 푸시가 끊겼다).
    // 등록을 풀었으므로 register() 를 다시 불러 받은 새 토큰으로 등록한다
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await vi.waitFor(() => expect(__fake.pushTokens).toEqual([{ platform: 'ios', token: 'device-token-2' }]));
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
    expect(getPushState()).toBe('denied'); // 푸시 토글이 켜져 있으면 설정 화면이 PUSH_OS_DENIED_NOTE('기기 설정에서 이 앱의 알림이 꺼져 있어요…')를 보여준다
    expect(await initPush({ plugin, platform: 'android' })).toBe(false); // 이미 시작됨
    __resetPush();
    expect(await initPush()).toBe(false); // jsdom = 웹
  });

  // 대역의 'pushNotificationActionPerformed' 리스너를 직접 불러 탭을 흉내 내고, pushTarget 의 경로 표도 확인한다
  it('알림을 탭하면 data.type 에 맞는 화면으로 간다', async () => {
    const onOpen = vi.fn();
    const plugin = fakePlugin();
    await initPush({ plugin, platform: 'ios', onOpen });
    plugin.listeners.pushNotificationActionPerformed({ notification: { data: { type: 'live', bookingId: 'b1' } } });
    expect(onOpen).toHaveBeenCalledWith({ type: 'live', bookingId: 'b1' });
    expect(pushTarget({ type: 'live', bookingId: 'b1' })).toBe('/service/live?booking=b1');
    expect(pushTarget({ type: 'recording', bookingId: 'b1' })).toBe('/service/live?booking=b1&replay=1');
    expect(pushTarget({ type: 'reservation', bookingId: 'b1' })).toBe('/reservations');
    expect(pushTarget({})).toBe('/home');
  });
});

describe('앱이 앞에 떠 있을 때의 푸시 (자체 점검 후속 검토)', () => {
  it('OS 배너를 띄우도록 설정돼 있다 — 없으면 켜 둔 앱에서는 지각 안내 같은 푸시가 보이지 않는다', async () => {
    const config = (await import('../../capacitor.config.json')).default; // apps/customer/capacitor.config.json
    expect(config.plugins.PushNotifications.presentationOptions).toEqual(expect.arrayContaining(['banner', 'list', 'alert']));
  });
});
