// manager.js 의 로그인·로그아웃·탈퇴가 localStorage 에 남은 가입 초안(약관 동의)을 지우는지 검증한다.
// 서버 대신 test/fakeHttp.js 의 가짜 서버를 쓴다 — 가짜 로그인은 WRONG_PASSWORD 가 아니면 어떤 번호·비밀번호도 통과한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { loginWithPhone, logout, deleteAccount, agreeManagerTerms, getAgreedTermsDraft, isSignedIn } from './manager.js';
import { session } from './session.js';
// __fake 는 진짜 http.js 에는 없다. 아래 vi.mock 이 바꿔 끼운 가짜 모듈의 상태(호출 기록 등)다
import { __fake } from './http.js';

// './http.js' 를 test/fakeHttp.js 의 가짜 서버로 바꾼다 — import 보다 먼저 적용돼 manager.js 도 가짜 http 를 쓴다
vi.mock('./http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

describe('manager api — 가입 초안(localStorage) 정리', () => {
  // 테스트마다 저장소·세션·가짜 서버 호출 기록을 비우고 시작한다
  beforeEach(() => {
    localStorage.clear();
    session.clear();
    __fake.calls.length = 0;
  });

  // 준비: 단계마다 약관 동의 초안을 먼저 남긴다 → 로그인 / 로그아웃 / 탈퇴를 차례로 하고
  // → 매번 초안이 빈 배열인지와 로그인 상태(isSignedIn)를 확인한다
  it('로그인·로그아웃·탈퇴는 다른 사람이 남긴 약관 동의 초안을 지운다 (공용 기기)', async () => {
    await agreeManagerTerms(['service', 'privacy', 'marketing']);
    expect(getAgreedTermsDraft()).toEqual(['service', 'privacy', 'marketing']);

    await loginWithPhone({ phone: '12345678', password: 'password1' });
    expect(getAgreedTermsDraft()).toEqual([]); // 가입 미완료 계정이 앞 사람의 동의(마케팅 포함)로 완료되지 않게
    expect(isSignedIn()).toBe(true);

    await agreeManagerTerms(['service']);
    await logout();
    expect(getAgreedTermsDraft()).toEqual([]);
    expect(isSignedIn()).toBe(false);

    await loginWithPhone({ phone: '12345678', password: 'password1' });
    await agreeManagerTerms(['service']);
    await deleteAccount('password1');
    expect(getAgreedTermsDraft()).toEqual([]);
    expect(isSignedIn()).toBe(false);
  });
});
