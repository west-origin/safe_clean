import { describe, it, expect, beforeEach, vi } from 'vitest';
import * as customer from './customer.js';
import { session } from './session.js';
import { seedSocialSession } from '../test/socialSession.js';
import { __fake } from './http.js';
vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());
vi.mock('../auth/client.js', () => ({ signOutAuth: vi.fn(async () => {}) }));
describe('customer social auth API', () => {
  beforeEach(() => { localStorage.clear(); sessionStorage.clear(); session.clear(); __fake.calls.length = 0; });
  it('removes password and direct phone login APIs', () => {
    for (const name of ['loginWithPhone', 'saveProfileInfo', 'resetPassword', 'checkPassword', 'requestPasswordReset', 'signUpWith']) expect(customer[name]).toBeUndefined();
  });
  it('records terms and completes the authenticated social profile', async () => {
    await seedSocialSession();
    await customer.agreeTerms(['service', 'privacy', 'location']);
    await customer.completeSignUp();
    expect(__fake.calls.at(-1)).toMatchObject({ method: 'POST', path: '/auth/signup/complete', body: { agreedTermIds: ['service', 'privacy', 'location'] } });
  });
  it('logs out and clears private reservation drafts', async () => {
    await seedSocialSession();
    sessionStorage.setItem('ansimclean.booking.home', 'private');
    await customer.logout();
    expect(session.accessToken()).toBeNull();
    expect(sessionStorage.getItem('ansimclean.booking.home')).toBeNull();
  });
  it('deletes via the social reauthentication endpoint without a password', async () => {
    await seedSocialSession();
    await customer.deleteAccount();
    expect(__fake.calls.at(-1)).toMatchObject({ method: 'DELETE', path: '/auth/account/social' });
    expect(session.accessToken()).toBeNull();
  });
  it('does not reuse another account terms draft', async () => {
    await seedSocialSession();
    await customer.agreeTerms(['service', 'privacy']);
    session.set({ accessToken: 'new', refreshToken: 'new', expiresIn: 3600, user: { id: 'other-account', role: 'customer' } });
    expect(customer.getAgreedTermsDraft()).toEqual([]);
  });

});
