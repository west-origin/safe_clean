import { beforeEach, describe, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ native: false, platform: 'web', signIn: vi.fn(), oauth: vi.fn(), idToken: vi.fn(), exchange: vi.fn() }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => mocks.native, getPlatform: () => mocks.platform }, registerPlugin: () => ({ signIn: mocks.signIn }) }));
vi.mock('./client.js', () => ({ initializeAuth: vi.fn(async () => {}), getAuthClient: () => ({ auth: { signInWithOAuth: mocks.oauth, signInWithIdToken: mocks.idToken, exchangeCodeForSession: mocks.exchange } }) }));
import { exchangeCallback, socialLogin, loginError } from './socialLogin.js';
import { isAuthCallback } from './config.js';
beforeEach(() => { vi.clearAllMocks(); mocks.native = false; mocks.platform = 'web'; });
describe('social authentication security boundaries', () => {
  it('accepts only the exact registered callback', async () => {
    expect(isAuthCallback('ansimclean://auth/callback?code=proof', true)).toBe(true);
    expect(isAuthCallback('ansimclean://evil/callback?code=proof', true)).toBe(false);
    expect(isAuthCallback('https://evil.test/auth/callback?code=proof')).toBe(false);
    await expect(exchangeCallback('https://evil.test/auth/callback?code=proof')).rejects.toThrow();
    expect(mocks.exchange).not.toHaveBeenCalled();
  });
  it('exchanges only a PKCE authorization code, rejecting URL tokens', async () => {
    mocks.exchange.mockResolvedValue({ error: null });
    await exchangeCallback('ansimclean://auth/callback?code=one-use', true);
    expect(mocks.exchange).toHaveBeenCalledWith('one-use');
    await expect(exchangeCallback('ansimclean://auth/callback#access_token=secret', true)).rejects.toThrow();
  });
  it('does not start a browser login after native cancellation', async () => {
    mocks.native = true; mocks.platform = 'android';
    mocks.signIn.mockRejectedValue({ code: 'CANCELLED' });
    await expect(socialLogin('google')).rejects.toMatchObject({ code: 'CANCELLED' });
    expect(mocks.oauth).not.toHaveBeenCalled();
  });
  it('uses Supabase for native ID token validation without client linking', async () => {
    mocks.native = true; mocks.platform = 'android';
    mocks.signIn.mockResolvedValue({ idToken: 'native-id-token' });
    mocks.idToken.mockResolvedValue({ error: null });
    await socialLogin('kakao');
    expect(mocks.idToken).toHaveBeenCalledWith(expect.objectContaining({ provider: 'kakao', token: 'native-id-token', nonce: expect.any(String) }));
    expect(mocks.oauth).not.toHaveBeenCalled();
  });
  it('does not request default personal scopes when Kakao SDK fails', async () => {
    mocks.native = true; mocks.platform = 'android';
    mocks.signIn.mockRejectedValue({ code: 'OIDC_REQUIRED' });
    await expect(socialLogin('kakao')).rejects.toMatchObject({ code: 'OIDC_REQUIRED' });
    expect(mocks.oauth).not.toHaveBeenCalled();
  });
  it('blocks the web Kakao OAuth route while personal scopes cannot be excluded', async () => {
    await expect(socialLogin('kakao')).rejects.toMatchObject({ code: 'KAKAO_BROWSER_UNAVAILABLE' });
    expect(mocks.oauth).not.toHaveBeenCalled();
  });
  it('does not echo provider tokens or raw errors into UI messages', () => {
    expect(loginError(new Error('access_token=secret'))).not.toContain('secret');
  });
  it('distinguishes provider failures from user cancellation', async () => {
    await expect(exchangeCallback('ansimclean://auth/callback?error=access_denied', true)).rejects.toMatchObject({ code: 'CANCELLED' });
    const failure = await exchangeCallback('ansimclean://auth/callback?error=server_error&error_description=secret', true).catch(error => error);
    expect(failure.code).not.toBe('CANCELLED');
    expect(failure.message).not.toContain('secret');
  });

});
