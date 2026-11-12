import { session } from '../api/session.js';
import { fakeHttpModule } from './fakeHttp.js';
/** Seed a verified social session without invoking retired customer password APIs. */
export async function seedSocialSession() {
  const { __fake } = fakeHttpModule();
  session.set({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 3600, user: __fake.user });
  return { ok: true, user: __fake.user };
}
