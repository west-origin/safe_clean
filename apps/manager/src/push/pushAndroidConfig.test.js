// Android 에 Firebase 설정(google-services.json)이 없는 빌드면 initPush 가 FCM 플러그인을 부르지 않는지 본다.
// 부르면 앱이 'FirebaseApp is not initialized' 로 죽는다 (2026-11-04 에뮬레이터에서 매니저 앱이 켜지자마자 꺼지던 원인).
import { afterEach, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ register: vi.fn(), addListener: vi.fn(), checkPermissions: vi.fn() }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true, getPlatform: () => 'android' } }));
vi.mock('@capacitor/push-notifications', () => ({ PushNotifications: mocks }));
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

import { initPush, getPushState, __resetPush } from './pushClient.js';

afterEach(() => {
  vi.unstubAllEnvs();
  __resetPush();
  vi.clearAllMocks();
});

it('설정이 없으면 플러그인을 부르지 않고 등록 실패로 둔다', async () => {
  vi.stubEnv('VITE_ANDROID_PUSH', 'false');
  expect(await initPush()).toBe(false);
  expect(mocks.addListener).not.toHaveBeenCalled();
  expect(mocks.register).not.toHaveBeenCalled();
  expect(getPushState()).toBe('error');
});

it('설정이 있으면 등록한다', async () => {
  vi.stubEnv('VITE_ANDROID_PUSH', 'true');
  mocks.checkPermissions.mockResolvedValue({ receive: 'granted' });
  expect(await initPush()).toBe(true);
  expect(mocks.register).toHaveBeenCalledOnce();
});
