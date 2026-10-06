// 고객 앱 api/http.js(모든 서버 호출이 거치는 함수) 테스트.
// 확인하는 것: 401 뒤 refresh 한 번 → 성공이면 재시도·무효면 세션 삭제·장애면 세션 유지(NETWORK),
// 기다리는 사이 로그아웃·다른 계정 로그인이 끼어들 때 옛 응답이 세션을 바꾸지 않는지, FormData 업로드 헤더, 본문 없는 오류 문구.
// 서버는 띄우지 않는다 — vi.stubGlobal('fetch', …) 로 전역 fetch 를 가짜 함수로 바꿔, 부를 때마다 정해 둔 응답을 돌려준다.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { http } from './http.js';
import { session } from './session.js';

/** 401 → refresh 흐름. refresh 가 네트워크·서버 장애로 실패한 것은 토큰이 무효한 게 아니다 — 세션을 지우지 않는다 */
describe('http — 토큰 갱신', () => {
  // 가짜 fetch 응답(Response 흉내). http.js 가 쓰는 ok·status·text() 만 있으면 된다
  const json = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body), json: async () => body });

  // 테스트마다 로그인된 세션(access 'old', refresh 'r')에서 시작한다
  beforeEach(() => {
    session.set({ accessToken: 'old', refreshToken: 'r', expiresIn: 3600, user: { profileComplete: true } });
  });
  // 가짜 fetch 를 원래대로 되돌리고 세션을 비워 다음 테스트에 남지 않게 한다
  afterEach(() => {
    vi.unstubAllGlobals();
    session.clear();
  });

  // mockResolvedValueOnce 는 부른 순서대로 한 번씩 쓰인다: 첫째 = 원래 요청(401), 둘째 = /auth/refresh(401)
  it('refresh 가 401 이면 세션을 지우고 401 을 던진다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } })).mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } })));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ status: 401 });
    expect(session.accessToken()).toBeNull();
  });

  // 둘째 호출(refresh)에서 fetch 자체가 실패(TypeError)하는 것 = 네트워크 끊김
  it('refresh 중 네트워크가 끊기면 세션은 그대로 두고 NETWORK 로 던진다 (강제 로그아웃 아님)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } })).mockRejectedValueOnce(new TypeError('offline')));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ code: 'NETWORK' });
    expect(session.refreshToken()).toBe('r');
  });

  it('refresh 가 5xx·429 여도 세션을 지우지 않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } })).mockResolvedValueOnce(json(502, { error: { code: 'UPSTREAM' } })));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ code: 'NETWORK' });
    expect(session.refreshToken()).toBe('r');
  });

  // 원래 요청 401 → refresh 200(새 토큰) → 재시도 200. 셋째 호출(재시도)의 헤더에 새 토큰이 붙었는지 본다
  it('refresh 가 성공하면 새 토큰으로 한 번 다시 부른다', async () => {
    const fetch = vi.fn()
      .mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockResolvedValueOnce(json(200, { accessToken: 'new', refreshToken: 'r2', expiresIn: 3600 }))
      .mockResolvedValueOnce(json(200, { ok: true }));
    vi.stubGlobal('fetch', fetch);
    await expect(http.get('/customer/home')).resolves.toEqual({ ok: true });
    expect(session.accessToken()).toBe('new');
    expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer new'); // calls[2][1] = 셋째 호출의 둘째 인자(init)
  });
});

/** 로그아웃과 refresh 가 겹칠 때, 그리고 사진 업로드(FormData) 요청 모양 */
describe('http — 계정 전환·파일 업로드 (외부 검토 3차)', () => {
  const json = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body), json: async () => body });
  afterEach(() => {
    vi.unstubAllGlobals();
    session.clear();
  });

  // 준비: refresh 응답을 테스트가 원할 때 보내도록 붙잡아 둔다 → 그 사이 로그아웃 → 늦게 새 토큰을 보낸다.
  // 확인: 요청은 401 로 끝나고, 늦은 토큰('zombie')이 세션에 들어오지 않는다
  it('refresh 응답이 로그아웃보다 늦게 오면 옛 계정을 되살리지 않는다', async () => {
    session.set({ accessToken: 'old', refreshToken: 'r', expiresIn: 3600, user: { profileComplete: true } });
    let resolveRefresh;
    const fetch = vi.fn()
      .mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockImplementationOnce(() => new Promise((r) => { resolveRefresh = r; })); // 둘째 호출(refresh)은 끝나지 않고 기다린다
    vi.stubGlobal('fetch', fetch);
    const pending = http.get('/customer/home');
    // vi.waitFor: 조건이 통과할 때까지 잠깐씩 다시 확인한다 — refresh 요청이 실제로 나갈 때까지 기다린다
    await vi.waitFor(() => expect(resolveRefresh).toBeTypeOf('function'));
    session.clear(); // refresh 를 기다리는 사이 로그아웃
    resolveRefresh(json(200, { accessToken: 'zombie', refreshToken: 'r2', expiresIn: 3600 }));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(session.accessToken()).toBeNull();
  });

  // 사진 업로드 요청 모양. 브라우저가 boundary(파트 구분 문자열)를 넣은 Content-Type 을 붙이도록
  // http.js 가 Content-Type 헤더만 넣지 않고(Authorization 등은 그대로), 본문도 JSON.stringify 없이 FormData 그대로 보내는지 본다
  it('FormData 본문은 JSON 으로 바꾸지 않고 Content-Type 도 브라우저에 맡긴다', async () => {
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: { profileComplete: true } });
    const fetch = vi.fn().mockResolvedValueOnce(json(200, { path: 'p', url: 'u' }));
    vi.stubGlobal('fetch', fetch);
    const form = new FormData();
    form.append('file', new Blob(['x'], { type: 'image/png' }), 'a.png');
    await expect(http.post('/customer/repair-photos', form)).resolves.toEqual({ path: 'p', url: 'u' });
    const init = fetch.mock.calls[0][1];
    expect(init.body).toBe(form);
    expect(init.headers['Content-Type']).toBeUndefined();
  });
});

/** A 계정 요청이 refresh 를 기다리는 사이 B 로 로그인한 경우 — 늦은 결과가 B 세션을 지우거나 바꾸면 안 된다 */
describe('http — 계정 전환 뒤 옛 401 (외부 검토 4차)', () => {
  const json = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body), json: async () => body });
  afterEach(() => {
    vi.unstubAllGlobals();
    session.clear();
  });

  // 준비: A 로그인 → 요청 401 → refresh 를 붙잡아 둔 채 B 로 로그인 → A 의 refresh 성공 응답을 늦게 보낸다.
  // 확인: A 요청은 401 로 끝나고, 세션은 B 의 토큰('b') 그대로다
  it('A 계정 요청의 401·refresh 무효가 B 로그인 뒤에 처리돼도 B 세션을 지우지 않는다', async () => {
    session.set({ accessToken: 'a', refreshToken: 'ra', expiresIn: 3600, user: { profileComplete: true } });
    let resolveRefresh;
    const fetch = vi.fn()
      .mockResolvedValueOnce(json(401, { error: { code: 'UNAUTHENTICATED' } }))
      .mockImplementationOnce(() => new Promise((r) => { resolveRefresh = r; }));
    vi.stubGlobal('fetch', fetch);
    const pending = http.get('/customer/home');
    await vi.waitFor(() => expect(resolveRefresh).toBeTypeOf('function'));
    session.set({ accessToken: 'b', refreshToken: 'rb', expiresIn: 3600, user: { profileComplete: true } }); // 그 사이 B 로 로그인
    resolveRefresh(json(200, { accessToken: 'a2', refreshToken: 'ra2', expiresIn: 3600 }));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(session.accessToken()).toBe('b');
  });
});

/** 서버가 우리 오류 모양을 주지 못한 응답(프록시 502·라우팅 404 …)의 문구 (사용성 검토 2026-09-22) */
describe('http — 본문 없는 오류 문구', () => {
  // JSON 이 아닌(또는 빈) 본문을 주는 실패 응답. 아래 상태 코드는 401 이 아니라 refresh 는 일어나지 않는다
  const raw = (status, text) => ({ ok: false, status, text: async () => text, json: async () => null });
  afterEach(() => vi.unstubAllGlobals());

  // mockResolvedValue(Once 없음)는 몇 번을 불러도 같은 응답 — 상태 코드마다 fetch 를 바꿔 끼우며 문구를 본다
  it('404·5xx·429 는 원인을 구분해 말한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(raw(404, '<html>Not Found</html>')));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ status: 404, message: '요청한 정보를 찾을 수 없어요' });

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(raw(502, '')));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해주세요' });

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(raw(429, '')));
    await expect(http.get('/customer/home')).rejects.toMatchObject({ message: '요청이 너무 많아요. 잠시 후 다시 시도해주세요' });
  });
});
