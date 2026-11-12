import { beforeEach, expect, it, vi } from 'vitest';
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => false } }));
import { sdkStorage, clearSdkStorage } from './storage.js';
const key = 'ansimclean.customer.supabase';
beforeEach(() => localStorage.clear());
it('a late refresh from a retired SDK cannot restore credentials after logout', async () => {
  const retired = sdkStorage();
  await retired.setItem(key, 'old-session');
  await clearSdkStorage(key);
  await retired.setItem(key, 'late-old-refresh');
  expect(localStorage.getItem(key)).toBeNull();
});
it('a late signout from a retired SDK cannot delete the new account session', async () => {
  const retired = sdkStorage();
  await retired.setItem(key, 'first-account');
  await clearSdkStorage(key);
  const current = sdkStorage();
  await current.setItem(key, 'second-account');
  await retired.removeItem(key);
  expect(await current.getItem(key)).toBe('second-account');
});
