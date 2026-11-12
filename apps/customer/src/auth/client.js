import { createClient } from '@supabase/supabase-js';
import { Capacitor } from '@capacitor/core';
import { sdkStorage, clearSdkStorage } from './storage.js';
const SDK_STORAGE_KEY = 'ansimclean.customer.supabase';
import { session } from '../api/session.js';
let client;
export function getAuthClient() {
  if (client) return client;
  const url = import.meta.env.VITE_SUPABASE_URL;
  const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;
  if (key?.startsWith('sb_secret_')) throw new Error('공개 키만 사용할 수 있어요. 앱 설정을 확인해주세요.');
  if (!url || !key) throw new Error('소셜 로그인 설정이 준비되지 않았어요. 고객센터에 문의해주세요.');
  client = createClient(url, key, { auth: {
    storage: sdkStorage(), storageKey: SDK_STORAGE_KEY,
    persistSession: true, autoRefreshToken: true, detectSessionInUrl: false, flowType: 'pkce',
  }});
  return client;
}
let initializing;
let authSubscription;
let appStateSubscription;
function mirror(value) {
  if (!value) { if (session.accessToken()) session.clear({ notifySdk: false }); return; }
  if (session.get()?.authUserId && session.get().authUserId !== value.user.id) session.clear({ notifySdk: false });
  const user = session.user()?.id === value.user.id ? session.user() : null;
  const next = { accessToken: value.access_token, refreshToken: value.refresh_token,
    expiresIn: Math.max(0, value.expires_at - Date.now() / 1000), user };
  if (session.accessToken() && session.get()?.authUserId === value.user.id) session.renew(next, session.generation());
  else session.set(next);
  session.get().authUserId = value.user.id;
}
export function initializeAuth() {
  return initializing ??= (async () => {
    await session.hydrate();
    const auth = getAuthClient().auth;
    const legacy = session.get();
    session.useSdk({
      async refresh() {
        const { data, error } = await auth.refreshSession();
        if (error) {
          if (error.status === 400 || error.status === 401 || error.status === 403) {
            await auth.signOut({ scope: 'local' });
            session.clear();
            return 'invalid';
          }
          return 'transient';
        }
        mirror(data.session);
        return data.session ? 'ok' : 'invalid';
      },
      clear: () => { void signOutAuth().catch(() => {}); },
    });
    authSubscription?.unsubscribe();
    authSubscription = auth.onAuthStateChange((_event, value) => mirror(value)).data.subscription;
    let { data, error } = await auth.getSession();
    if (!error && !data.session && legacy?.refreshToken) {
      ({ data, error } = await auth.setSession({ access_token: legacy.accessToken, refresh_token: legacy.refreshToken }));
    }
    if (error) {
      if ([400, 401, 403].includes(error.status)) {
        await auth.signOut({ scope: 'local' });
        mirror(null);
        await session.removeLegacyStorage();
        return;
      }
      throw new Error('저장된 로그인 정보를 확인하지 못했어요. 다시 시도해주세요.');
    }
    mirror(data.session);
    // Remove the old independently managed token copy after migration.
    await session.removeLegacyStorage();
    if (Capacitor.isNativePlatform()) {
      const { App } = await import('@capacitor/app');
      const launch = await App.getLaunchUrl();
      if (launch?.url) {
        const { isAuthCallback } = await import('./config.js');
        if (isAuthCallback(launch.url, true)) {
          const { exchangeCallback } = await import('./socialLogin.js');
          await exchangeCallback(launch.url, true);
        }
      }
      await appStateSubscription?.remove();
      appStateSubscription = await App.addListener('appStateChange', ({ isActive }) => {
        if (isActive) auth.startAutoRefresh(); else auth.stopAutoRefresh();
      });
      auth.startAutoRefresh();
    }
  })().catch(error => { initializing = null; throw error; });
}
export async function signOutAuth() {
  const retiring = client;
  const auth = retiring?.auth;
  // Close the old client before any new login. Local clearing still succeeds offline.
  authSubscription?.unsubscribe();
  authSubscription = null;
  const retiringAppListener = appStateSubscription;
  appStateSubscription = null;
  auth?.stopAutoRefresh();
  client = null;
  initializing = null;
  session.useSdk(null);
  session.clear({ notifySdk: false });
  await retiringAppListener?.remove();
  await clearSdkStorage(SDK_STORAGE_KEY);
  // The retired instance has an invalid storage epoch, so a late result cannot clear
  // or restore a newer session. Server revocation was also attempted by /auth/logout.
  if (auth) void auth.signOut({ scope: 'local' }).catch(() => {});
}
