import { Capacitor, registerPlugin } from '@capacitor/core';
import { getAuthClient, initializeAuth } from './client.js';
import { SOCIAL_PROVIDERS, NATIVE_AUTH_CALLBACK, isAuthCallback } from './config.js';
const NativeSocialAuth = registerPlugin('SocialAuth');
let pending = false;
const cancelled = () => Object.assign(new Error('로그인을 취소했어요.'), { code: 'CANCELLED' });
export function loginError(error) {
  if (/^KAKAO_(AUTH|API|CLIENT)_[A-Za-z0-9_]+$/.test(error?.code ?? '')) return `카카오 인증을 완료하지 못했어요. 오류 코드: ${error.code}`;
  if (error?.code === 'OIDC_REQUIRED') return '카카오 OpenID Connect 설정을 확인해주세요. 이메일 제공 없이 로그인할 수 있어요.';
  if (error?.code === 'KAKAO_BROWSER_UNAVAILABLE') return '현재 카카오 로그인은 Android 앱에서 이용해주세요. 웹에서는 구글로 로그인할 수 있어요.';
  if (error?.code === 'CANCELLED') return '로그인을 취소했어요. 다시 선택할 수 있어요.';
  if (!navigator.onLine || error instanceof TypeError) return '네트워크 연결을 확인하고 다시 시도해주세요.';
  return '로그인하지 못했어요. 이메일 제공 동의와 계정 상태를 확인한 후 다시 시도해주세요.';
}
export async function exchangeCallback(value, native = false) {
  if (!isAuthCallback(value, native)) throw new Error('잘못된 로그인 응답');
  const url = new URL(value);
  if (url.searchParams.get('error') === 'access_denied') throw cancelled();
  if (url.searchParams.has('error')) throw new Error('인증 서버에서 로그인을 완료하지 못했어요.');
  const code = url.searchParams.get('code');
  if (!code || url.hash) throw new Error('잘못된 로그인 응답');
  const { error } = await getAuthClient().auth.exchangeCodeForSession(code);
  if (error) throw error;
}
async function browserLogin(provider) {
  // Preserve the existing OAuth path for a later rollout, but do not request
  // Supabase's default email/photo/nickname scopes in the current minimal mode.
  if (provider === 'kakao') throw Object.assign(new Error('Kakao SDK required'), { code: 'KAKAO_BROWSER_UNAVAILABLE' });
  const native = Capacitor.isNativePlatform();
  const redirectTo = native ? NATIVE_AUTH_CALLBACK : `${window.location.origin}/auth/callback`;
  const { data, error } = await getAuthClient().auth.signInWithOAuth({ provider,
    options: { redirectTo, skipBrowserRedirect: true, scopes: provider === 'kakao' ? 'openid' : 'openid email profile' } });
  if (error || !data.url) throw error || new Error('로그인 주소 없음');
  const url = new URL(data.url);
  if (url.protocol !== 'https:') throw new Error('안전하지 않은 로그인 주소');
  if (!native) { window.location.assign(data.url); return { redirecting: true }; }
  const [{ App }, { Browser }] = await Promise.all([import('@capacitor/app'), import('@capacitor/browser')]);
  return new Promise((resolve, reject) => {
    let done = false;
    const listeners = [];
    const finish = async (error) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      await Promise.all(listeners.map(listener => listener.remove()));
      await Browser.close().catch(() => {});
      if (error) reject(error); else resolve({ redirecting: false });
    };
    const timer = setTimeout(() => { void finish(cancelled()); }, 180000);
    (async () => {
      listeners.push(await App.addListener('appUrlOpen', ({ url }) => {
        if (!done && isAuthCallback(url, true)) {
          // Browser.close can emit browserFinished; mark completion before closing.
          void exchangeCallback(url, true).then(() => finish(), error => finish(error));
        }
      }));
      listeners.push(await Browser.addListener('browserFinished', () => { void finish(cancelled()); }));
      await Browser.open({ url: data.url });
    })().catch(error => { void finish(error); });
  });
}
export async function socialLogin(provider) {
  if (!SOCIAL_PROVIDERS.includes(provider)) throw new Error('지원하지 않는 로그인');
  if (pending) return { pending: true };
  pending = true;
  try {
    await initializeAuth();
    if (Capacitor.getPlatform() === 'android') {
      const rawNonce = crypto.randomUUID();
      const hash = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(rawNonce));
      const nonce = [...new Uint8Array(hash)].map(value => value.toString(16).padStart(2, '0')).join('');
      try {
        const result = await NativeSocialAuth.signIn({ provider, nonce });
        if (!result.idToken) throw new Error('ID 토큰 없음');
        const { error } = await getAuthClient().auth.signInWithIdToken({ provider, token: result.idToken, nonce: rawNonce });
        if (error) throw error;
        return { redirecting: false };
      } catch (error) {
        if (error.code === 'CANCELLED') throw cancelled();
        // Kakao's SDK already handles the safe account-browser fallback.
        // Never fall back to the Supabase OAuth endpoint that adds personal scopes.
        if (provider === 'kakao') throw error;
        // SDK unavailable or authentication failed: use Supabase's PKCE browser flow.
      }
    }
    return await browserLogin(provider);
  } finally { pending = false; }
}
