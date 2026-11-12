import { beforeEach, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ check: vi.fn(), register: vi.fn(), addListener: vi.fn(), permissions: vi.fn() }));
vi.mock('@capacitor/core', () => ({
  Capacitor: { isNativePlatform: () => true, getPlatform: () => 'android' },
  registerPlugin: () => ({ check: mocks.check }),
}));
vi.mock('@capacitor/push-notifications', () => ({ PushNotifications: {
  addListener: mocks.addListener, register: mocks.register, checkPermissions: mocks.permissions,
} }));
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());
import { initPush, __resetPush } from './pushClient.js';
beforeEach(() => { __resetPush(); vi.clearAllMocks(); });
it('does not invoke FCM or attach listeners when Android Firebase is unconfigured', async () => {
  mocks.check.mockResolvedValue({ configured: false });
  expect(await initPush()).toBe(false);
  expect(mocks.register).not.toHaveBeenCalled();
  expect(mocks.addListener).not.toHaveBeenCalled();
});
it('registers push when Android Firebase is configured', async () => {
  mocks.check.mockResolvedValue({ configured: true });
  mocks.permissions.mockResolvedValue({ receive: 'granted' });
  expect(await initPush()).toBe(true);
  expect(mocks.register).toHaveBeenCalledOnce();
});
