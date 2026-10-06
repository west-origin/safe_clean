// DEV 가짜 서버 모드(VITE_API_MOCK=1, `npm run dev:customer:mock`)에서 api/http.js 가 오류를 넘기는 방식을 확인한다 (문제 목록 37번).
// 이 모드의 http.js 는 test/fakeHttp.js 의 http 만 불러 쓰는데, 가짜 서버가 던지는 오류는 그 파일이 따로 정의한 ApiError 다.
// 예전에는 그대로 넘겨 customer.js 의 `e instanceof ApiError`(http.js 의 클래스)가 늘 거짓이라, 예: 틀린 비밀번호가
// '로그인 실패' 안내 대신 일반 오류로 보였다. 다른 테스트와 달리 vi.mock 으로 http.js 를 바꾸지 않고 진짜 http.js 의 가짜 분기를 지난다.
import { describe, it, expect, vi, beforeAll, afterAll } from 'vitest';

describe('http — DEV 가짜 서버 모드', () => {
  // http.js 의 useFake 는 모듈을 처음 불러올 때 정해진다 → 환경 변수를 켠 뒤 resetModules 로 새로 불러온다 (= 가짜 모드로 앱을 새로 띄운 것).
  // 처음 불러오기는 ui 패키지까지 읽어 느릴 수 있어 한 번만 하고(시간 한도 30초) 두 테스트가 같이 쓴다
  let httpModule;
  let customer;
  let fake;
  beforeAll(async () => {
    vi.stubEnv('VITE_API_MOCK', '1');
    vi.resetModules();
    httpModule = await import('./http.js');
    customer = await import('./customer.js');
    fake = await import('../test/fakeHttp.js');
  }, 30_000);
  // 바꾼 환경 변수를 되돌린다
  afterAll(() => vi.unstubAllEnvs());

  it('가짜 서버가 던진 오류는 http.js 의 ApiError 로 올라온다 — status·code·message 는 그대로', async () => {
    const err = await httpModule.http.post('/auth/login', { phone: '12345678', password: fake.WRONG_PASSWORD, role: 'customer' }).catch((e) => e);
    expect(err).toBeInstanceOf(httpModule.ApiError);
    expect(err).toMatchObject({ status: 401, code: 'LOGIN_FAILED', message: '휴대폰 번호 또는 비밀번호를 확인해주세요' });
  });

  it('그래서 customer.js 의 instanceof 검사가 동작한다 — 틀린 비밀번호는 { ok: false }(로그인 실패 안내)', async () => {
    expect(await customer.loginWithPhone({ phone: '12345678', password: fake.WRONG_PASSWORD })).toEqual({ ok: false });
  });
});
