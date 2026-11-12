export const SOCIAL_PROVIDERS = ['kakao', 'google'];
export const ACCOUNT_RECOVERY_URLS = Object.freeze({
  kakao: 'https://accounts.kakao.com',
  google: 'https://accounts.google.com/signin/recovery',
});
export const NATIVE_AUTH_CALLBACK = 'ansimclean://auth/callback';
export function isAuthCallback(value, native = false) {
  try {
    const url = new URL(value);
    const expected = new URL(native ? NATIVE_AUTH_CALLBACK : `${window.location.origin}/auth/callback`);
    return url.origin === expected.origin && url.protocol === expected.protocol && url.host === expected.host && url.pathname === expected.pathname;
  } catch { return false; }
}
