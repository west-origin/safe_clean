// 요청이 오가는 도중에 계정이 바뀔 때(A 로그인 중 B 로 로그인, 또는 A 로그아웃 도중 B 로그인) http.js 가 A 의 늦은 응답을 B 에게 넘기지 않는지 본다.
// 가짜 서버(fakeHttp) 대신 fetch 자체를 가짜로 바꿔(vi.stubGlobal) 응답이 오는 시점을 테스트가 정한다.
// 고객 앱과 매니저 앱의 http.js·session.js 가 같은 규칙이라 두 앱을 같은 테스트로 돌린다 (매니저 파일은 상대 경로로 불러온다).
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// fetch 가 돌려주는 Response 의 흉내 — http.js 가 쓰는 ok·status·text() 만 있다
const json = (status, body) => ({ ok: status < 400, status, text: async () => JSON.stringify(body) });
// 계정 id 로 로그인한 것처럼 세션을 채운다 (access 토큰 = id, 예: 'a' → 'Bearer a'). session.set 은 세션 번호(generation)도 올린다
const login = (session, id) => session.set({ accessToken: id, refreshToken: `r-${id}`, expiresIn: 3600, user: { id, profileComplete: true } });

for (const app of ['customer', 'manager']) {
  describe(`${app} 요청과 계정의 수명`, () => {
    let session;
    let http;
    // 테스트마다 모듈을 새로 불러온다(vi.resetModules) — 세션 번호·진행 중 refresh 같은 모듈 안 상태가 앞 테스트에서 이어지지 않게. 계정 A 로 시작
    beforeEach(async () => {
      vi.resetModules();
      ({ session } = app === 'customer' ? await import('./session.js') : await import('../../../manager/src/api/session.js'));
      ({ http } = app === 'customer' ? await import('./http.js') : await import('../../../manager/src/api/http.js'));
      login(session, 'a');
    });
    // 가짜 시계·가짜 fetch 를 원래대로 되돌리고 세션을 비운다
    afterEach(() => {
      vi.useRealTimers();
      vi.unstubAllGlobals();
      session.clear();
    });

    // A 의 요청을 보내 둔 채(응답 보류) B 로 로그인 → 그제서야 A 요청에 401 을 준다.
    // 둘째·셋째 fetch 응답(갱신 성공·재전송 성공)은 잘못 갱신·재전송했을 때만 쓰인다 — fetch 가 한 번만 불렸는지로 확인한다
    it('A의 늦은 401을 B 토큰으로 갱신·재전송하지 않는다', async () => {
      let respond; // 보류해 둔 첫 응답을 테스트가 원하는 때 보내는 함수
      const fetch = vi.fn()
        .mockImplementationOnce(() => new Promise((resolve) => { respond = resolve; }))
        .mockResolvedValueOnce(json(200, { accessToken: 'b2', refreshToken: 'r-b2', expiresIn: 3600 }))
        .mockResolvedValueOnce(json(200, { ok: true }));
      vi.stubGlobal('fetch', fetch);
      // fetch 가 가짜라 경로는 실제 API 가 아니어도 된다
      const pending = http.post(`/${app}/inquiries`, { message: 'A의 비공개 문의' });
      const result = expect(pending).rejects.toMatchObject({ status: 401 }); // 거부를 받을 쪽을 응답보다 먼저 걸어 둔다 (기다림은 아래 await result)
      login(session, 'b');
      respond(json(401, { error: { code: 'UNAUTHENTICATED' } }));
      await result;
      expect(fetch).toHaveBeenCalledTimes(1);
      expect(session.accessToken()).toBe('b');
    });

    // A 로 보낸 /auth/me 의 성공 응답(A 의 정보)이 B 로그인 뒤에 도착 → 요청은 401(로그인 상태가 바뀜)로 끝나고 세션 사용자는 B 그대로
    it('A의 늦은 성공 응답을 B 화면·세션에 전달하지 않는다', async () => {
      let respond;
      vi.stubGlobal('fetch', vi.fn(() => new Promise((resolve) => { respond = resolve; })));
      const pending = http.get('/auth/me');
      const result = expect(pending).rejects.toMatchObject({ status: 401 });
      login(session, 'b');
      respond(json(200, { id: 'a', name: 'A' }));
      await result;
      expect(session.user().id).toBe('b');
    });

    // 서버가 409 IN_PROGRESS(같은 키 처리 중)를 주면 http.js 는 1초 기다렸다 한 번 더 보낸다.
    // 그 1초 사이에 B 로 로그인 → 다시 보내지 않고(fetch 1번) 401 로 끝나는지 본다
    it('멱등성 재시도 대기 중 계정이 바뀌면 다시 쓰지 않는다', async () => {
      vi.useFakeTimers(); // 가짜 시계: setTimeout 이 실제로 기다리지 않고 advanceTimersByTimeAsync 로 시간을 넘긴다
      const fetch = vi.fn()
        .mockResolvedValueOnce(json(409, { error: { code: 'IN_PROGRESS' } }))
        .mockResolvedValueOnce(json(200, { ok: true }));
      vi.stubGlobal('fetch', fetch);
      const pending = http.post(`/${app}/bookings`, { address: 'A 주소' }, { idempotencyKey: 'request-a' });
      const result = expect(pending).rejects.toMatchObject({ status: 401 });
      await vi.advanceTimersByTimeAsync(0); // 첫 응답(409)을 처리하고 1초 대기에 들어갈 때까지 진행
      login(session, 'b');
      await vi.advanceTimersByTimeAsync(1000);
      await result;
      expect(fetch).toHaveBeenCalledTimes(1);
    });

    // A 의 토큰으로 두 요청을 동시에 보내 둘 다 401 → refresh 는 한 번만 나가고(응답 보류),
    // 새 토큰 a2 가 오면 두 요청이 각각 새 토큰으로 다시 가서 성공한다
    it('같은 로그인에서 동시에 만료된 요청은 한 번 갱신하고 각각 재시도한다', async () => {
      let respond;
      // 주소·헤더로 응답을 고르는 가짜 fetch: refresh 는 보류, 옛 토큰('Bearer a')은 401, 그 밖(새 토큰)은 성공
      const fetch = vi.fn((url, init) => {
        if (url.endsWith('/auth/refresh')) return new Promise((resolve) => { respond = resolve; });
        if (init.headers.Authorization === 'Bearer a') return Promise.resolve(json(401, { error: { code: 'UNAUTHENTICATED' } }));
        return Promise.resolve(json(200, { ok: true }));
      });
      vi.stubGlobal('fetch', fetch);
      const first = http.get(`/${app}/home`);
      const second = http.get(`/${app}/inquiries`);
      await vi.waitFor(() => expect(respond).toBeTypeOf('function')); // refresh 요청이 나갈 때까지 기다린다
      respond(json(200, { accessToken: 'a2', refreshToken: 'r-a2', expiresIn: 3600 }));
      await expect(Promise.all([first, second])).resolves.toEqual([{ ok: true }, { ok: true }]);
      expect(fetch.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1);
    });

    // A 가 로그아웃을 누른 뒤 첫 서버 요청의 응답이 보류된 사이 B 로 로그인한다. 두 경우를 돌린다:
    // 'push' = 기기 토큰이 있어 첫 요청이 DELETE /push/token, 'logout' = 기기 토큰이 없어 첫 요청이 POST /auth/logout.
    // 기대: 다음 요청을 보내지 않고(fetch 1번), B 의 세션을 지우지 않으며, 푸시 등록 중지(suspend)도 풀려 있다
    it.each(['push', 'logout'])('로그아웃의 %s 응답이 늦어도 B의 서버·로컬 세션을 지우지 않는다', async (step) => {
      const api = app === 'customer' ? await import('./customer.js') : await import('../../../manager/src/api/manager.js');
      const { deviceToken } = app === 'customer' ? await import('../push/deviceToken.js') : await import('../../../manager/src/push/deviceToken.js');
      deviceToken.set(step === 'push' ? 'device' : null, 'ios');
      let respond;
      const fetch = vi.fn()
        .mockImplementationOnce(() => new Promise((resolve) => { respond = resolve; }))
        .mockResolvedValue(json(200, { ok: true }));
      vi.stubGlobal('fetch', fetch);
      const pending = api.logout();
      login(session, 'b');
      respond(json(200, { ok: true }));
      await pending;
      expect(session.accessToken()).toBe('b');
      expect(fetch).toHaveBeenCalledTimes(1);
      expect(deviceToken.suspended()).toBe(false);
      deviceToken.set(null, null); // 기기 토큰 정리 (다음 테스트에 남지 않게)
    });
  });
}
