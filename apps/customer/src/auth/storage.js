import { Capacitor } from '@capacitor/core';
// Supabase owns token persistence. Native tokens never fall back to localStorage.
let queue = Promise.resolve();
async function secure() {
  // Capacitor proxies synthesize every property, including `then`.
  // Wrap the proxy so async return does not treat it as a Promise.
  return { plugin: (await import('capacitor-secure-storage-plugin')).SecureStoragePlugin };
}
export const authStorage = {
  async getItem(key) {
    await queue;
    if (!Capacitor.isNativePlatform()) return localStorage.getItem(key);
    const { plugin: store } = await secure();
    const { value: exists } = await store.keys();
    if (!exists.includes(key)) return null;
    return (await store.get({ key })).value;
  },
  setItem(key, value) {
    const next = queue.then(async () => {
      if (!Capacitor.isNativePlatform()) return localStorage.setItem(key, value);
      const { plugin: store } = await secure();
      await store.set({ key, value });
    });
    queue = next.catch(() => {});
    return next;
  },
  removeItem(key) {
    const next = queue.then(async () => {
      if (!Capacitor.isNativePlatform()) return localStorage.removeItem(key);
      const { plugin: store } = await secure();
      const { value: keys } = await store.keys();
      if (keys.includes(key)) await store.remove({ key });
    });
    queue = next.catch(() => {});
    return next;
  },
};

// Retired SDK instances cannot overwrite a new login after a slow sign-out response.
let storageEpoch = 0;
export function sdkStorage() {
  const epoch = storageEpoch;
  return {
    getItem: key => epoch === storageEpoch ? authStorage.getItem(key) : Promise.resolve(null),
    setItem: (key, value) => epoch === storageEpoch ? authStorage.setItem(key, value) : Promise.resolve(),
    removeItem: key => epoch === storageEpoch ? authStorage.removeItem(key) : Promise.resolve(),
  };
}
export async function clearSdkStorage(key) {
  storageEpoch++;
  await authStorage.removeItem(key);
  await authStorage.removeItem(`${key}-code-verifier`);
}
