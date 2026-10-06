// customer.js 의 로그인·가입·로그아웃·탈퇴 함수 테스트. 진짜 서버 대신 가짜 서버(test/fakeHttp.js)를 쓴다.
// 확인하는 것: 어떤 경로·본문으로 부르는지(__fake.calls), 세션·가입 초안이 제대로 남고 지워지는지.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { loginWithPhone, requestPhoneVerification, verifyPhone, saveProfileInfo, completeSignUp, agreeTerms, logout, deleteAccount, isSignedIn, signupStep } from './customer.js';
import { session } from './session.js';
// 아래 vi.mock 때문에 이 import 는 가짜 서버 모듈에서 온다. __fake = 가짜 서버의 호출 기록·상태
import { __fake } from './http.js';

vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

/** 어댑터 함수가 docs/api.md 의 경로·본문으로 부르고, 응답을 화면 계약(README)대로 바꾸는지 */
describe('customer api — 인증 어댑터', () => {
  // 테스트마다 저장소·세션·호출 기록을 비워 앞 테스트의 로그인·초안이 남지 않게 한다
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session.clear();
    __fake.calls.length = 0;
  });

  it('loginWithPhone: /auth/login 에 role=customer 로 부르고 세션을 저장한다. 실패는 { ok:false } 로 (던지지 않는다)', async () => {
    const res = await loginWithPhone({ phone: '12345678', password: 'password1' });
    expect(res.ok).toBe(true);
    expect(__fake.calls[0]).toMatchObject({ method: 'POST', path: '/auth/login', body: { phone: '12345678', password: 'password1', role: 'customer' } });
    expect(isSignedIn()).toBe(true);
    // 'wrong-password' 는 가짜 서버가 LOGIN_FAILED(401)로 거부하는 비밀번호다
    expect(await loginWithPhone({ phone: '12345678', password: 'wrong-password' })).toEqual({ ok: false });
  });

  it('가입 흐름: 약관 초안 → 인증번호 → 검증 토큰 초안 → signup(세션, 가입 중) → complete(약관 전송, 완료)', async () => {
    await agreeTerms(['service', 'privacy', 'location']);
    expect((await requestPhoneVerification('12345678')).sent).toBe(true);
    // '00000000' = 가짜 서버가 '이미 가입한 번호'(PHONE_TAKEN)로 다루는 번호
    expect(await requestPhoneVerification('00000000')).toMatchObject({ sent: false, reason: 'taken' });
    // '123456' = 가짜 서버가 맞다고 보는 인증번호. 성공하면 가짜 인증 토큰 'tok' 이 초안에 들어간다
    await verifyPhone('12345678', '123456');
    await saveProfileInfo({ name: '김하늘', nickname: '하늘', password: 'password1', phone: '12345678' });
    // 초안의 인증 토큰은 가고, 화면이 넘긴 phone 은 서버 본문에 들어가지 않는다
    expect(__fake.calls.find((c) => c.path === '/auth/signup').body).toEqual({ verificationToken: 'tok', name: '김하늘', nickname: '하늘', password: 'password1' });
    // 계정은 생겼지만 가입이 덜 끝났다 — 로그인 상태가 아니고, 다음 단계는 주소 입력
    expect(isSignedIn()).toBe(false);
    expect(signupStep()).toBe('address');
    await completeSignUp();
    expect(__fake.calls.find((c) => c.path === '/auth/signup/complete').body).toEqual({ agreedTermIds: ['service', 'privacy', 'location'] });
    expect(isSignedIn()).toBe(true);
    expect(localStorage.getItem('ansimclean.signup')).toBeNull(); // 가입이 끝나면 초안(약관 체크·인증 토큰)을 지운다
  });

  it('logout 과 deleteAccount 는 세션을 지운다', async () => {
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await logout();
    expect(isSignedIn()).toBe(false);
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await deleteAccount('password1');
    expect(isSignedIn()).toBe(false);
    expect(__fake.calls.at(-1)).toMatchObject({ method: 'DELETE', path: '/auth/account', body: { password: 'password1' } });
  });
});

describe('customer api — 예약 초안 정리 (외부 검토 3차)', () => {
  // 앞 사람이 남긴 예약 초안 두 개를 sessionStorage 에 직접 넣고 → 로그인·로그아웃 → 둘 다 지워졌는지 본다.
  // 지우는 쪽은 session.js 의 clear() 다 (이 테스트는 screens/booking/bookingDraft.js 를 불러오지 않는다)
  it('bookingDraft 모듈이 로드되지 않았어도 로그아웃하면 sessionStorage 의 예약 초안이 지워진다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ roadAddress: '앞 사람 주소' }));
    sessionStorage.setItem('ansimclean.booking.repair:appliance', JSON.stringify({ detail: '앞 사람 증상' }));
    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await logout();
    expect(sessionStorage.getItem('ansimclean.booking.home')).toBeNull();
    expect(sessionStorage.getItem('ansimclean.booking.repair:appliance')).toBeNull();
  });
});
