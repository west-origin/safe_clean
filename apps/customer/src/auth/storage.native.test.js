import { expect, it, vi } from 'vitest';
const { values, thenRead, plugin } = vi.hoisted(() => {
  const values = new Map();
  const thenRead = vi.fn();
  const methods = {
    keys: async () => ({ value: [...values.keys()] }),
    get: async ({ key }) => ({ value: values.get(key) }),
    set: async ({ key, value }) => { values.set(key, value); },
    remove: async ({ key }) => { values.delete(key); },
  };
  const plugin = new Proxy(methods, { get(target, name) {
    if (name === 'then') { thenRead(); return () => {}; }
    return target[name];
  } });
  return { values, thenRead, plugin };
});
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true } }));
vi.mock('capacitor-secure-storage-plugin', () => ({ SecureStoragePlugin: plugin }));
import { authStorage } from './storage.js';
it('reads and writes native storage without awaiting the Capacitor proxy', async () => {
  expect(await authStorage.getItem('missing')).toBeNull();
  await authStorage.setItem('session', 'value');
  expect(await authStorage.getItem('session')).toBe('value');
  await authStorage.removeItem('session');
  expect(values.size).toBe(0);
  expect(thenRead).not.toHaveBeenCalled();
});
