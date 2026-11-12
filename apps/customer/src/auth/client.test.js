import { beforeEach, describe, expect, it, vi } from 'vitest';
const authMocks = vi.hoisted(() => ({ listener: null, auth: null }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => false } }));
vi.mock('@supabase/supabase-js', () => ({ createClient: () => ({ auth: authMocks.auth }) }));
const sdkSession = () => ({ access_token: 'access-one', refresh_token: 'refresh-one', expires_at: Date.now()/1000+3600, user: { id: 'social-one' } });
async function setup(value = sdkSession()) {
  vi.resetModules();
  localStorage.clear(); sessionStorage.clear();
  vi.stubEnv('VITE_SUPABASE_URL', 'https://test.supabase.co');
  vi.stubEnv('VITE_SUPABASE_PUBLISHABLE_KEY', 'sb_publishable_test');
  authMocks.auth = {
    onAuthStateChange: vi.fn(listener => { authMocks.listener = listener; return { data: { subscription: { unsubscribe: vi.fn() } } }; }),
    stopAutoRefresh: vi.fn(),
    getSession: vi.fn(async () => ({ data: { session: value }, error: null })),
    refreshSession: vi.fn(async () => ({ data: { session: value }, error: null })),
    signOut: vi.fn(async () => { authMocks.listener?.('SIGNED_OUT', null); return { error: null }; }),
    setSession: vi.fn(async () => ({ data: { session: value }, error: null })),
  };
  const client = await import('./client.js');
  const { session } = await import('../api/session.js');
  return { ...client, session };
}
beforeEach(() => vi.clearAllMocks());
describe('official Supabase session ownership', () => {
  it('restores the SDK session without persisting a duplicate refresh token', async () => {
    const { initializeAuth, session } = await setup();
    await initializeAuth();
    expect(session.accessToken()).toBe('access-one');
    session.setUser({ id: 'social-one', role: 'customer', profileComplete: true });
    expect(session.isSignedIn()).toBe(true);
    expect(localStorage.getItem('ansimclean.customer.auth')).toBeNull();
  });
  it('uses the SDK for refresh and preserves the login generation', async () => {
    const { initializeAuth, session } = await setup();
    await initializeAuth();
    const generation = session.generation();
    authMocks.auth.refreshSession.mockResolvedValue({ data: { session: { ...sdkSession(), access_token: 'access-two' } }, error: null });
    expect(await session.refreshWithSdk()).toBe('ok');
    expect(session.accessToken()).toBe('access-two');
    expect(session.generation()).toBe(generation);
  });
  it('clears the session on invalid refresh and keeps transient failures retryable', async () => {
    const { initializeAuth, session } = await setup();
    await initializeAuth();
    authMocks.auth.refreshSession.mockResolvedValue({ data: {}, error: { status: 503 } });
    expect(await session.refreshWithSdk()).toBe('transient');
    expect(session.accessToken()).toBe('access-one');
    authMocks.auth.refreshSession.mockResolvedValue({ data: {}, error: { status: 400 } });
    expect(await session.refreshWithSdk()).toBe('invalid');
    expect(session.accessToken()).toBeNull();
  });
  it('does not keep stale legacy credentials after an invalid startup refresh', async () => {
    const { initializeAuth, session } = await setup(null);
    authMocks.auth.getSession.mockResolvedValue({ data: { session: null }, error: { status: 400 } });
    await initializeAuth();
    expect(session.accessToken()).toBeNull();
  });
  it('removes the prior account drafts on an SDK account switch', async () => {
    const { initializeAuth, session } = await setup();
    await initializeAuth();
    sessionStorage.setItem('ansimclean.booking.home', 'private address');
    authMocks.listener('SIGNED_IN', { ...sdkSession(), user: { id: 'social-two' } });
    expect(sessionStorage.getItem('ansimclean.booking.home')).toBeNull();
    expect(session.user()).toBeNull();
  });
  it('clears persistent SDK credentials even when offline sign-out fails', async () => {
    const { initializeAuth, signOutAuth, session } = await setup();
    await initializeAuth();
    localStorage.setItem('ansimclean.customer.supabase', 'persisted credentials');
    authMocks.auth.signOut.mockRejectedValue(new Error('offline'));
    await signOutAuth();
    expect(session.accessToken()).toBeNull();
    expect(localStorage.getItem('ansimclean.customer.supabase')).toBeNull();
    expect(authMocks.auth.stopAutoRefresh).toHaveBeenCalled();
  });

});
