// 네이티브 세션 저장 (감사 M-01): 두 앱의 session.js 를 같은 시나리오로 확인한다
// 확인하는 것: 토큰을 localStorage 평문으로 남기지 않는다, 옛 평문 세션은 보안 저장소로 옮긴다,
// 삭제가 실패하거나 저장 순서가 꼬여도 다음 실행에서 로그아웃한 세션이 되살아나지 않는다.
// 실제 기기 없이 돌리려고 Capacitor 는 "네이티브다" 라고 답하게, 보안 저장소 플러그인은 가짜(get·set·remove 목)로 바꾼다.
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';

// vi.mock 은 파일 맨 위로 끌어올려 먼저 실행된다. 그 안에서 쓸 목 객체도 vi.hoisted 로 같이 먼저 만들어 둔다
const secure = vi.hoisted(() => ({ get: vi.fn(), set: vi.fn(), remove: vi.fn() }));
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => true } }));
vi.mock('capacitor-secure-storage-plugin', () => ({ SecureStoragePlugin: secure }));

// 같은 테스트를 고객 앱·매니저 앱 session.js 에 한 번씩 돌린다 (테스트에서 달라지는 것은 저장 키뿐이다)
for (const app of ['customer', 'manager']) {
  describe(`${app} 네이티브 세션`, () => {
    const key = `ansimclean.${app}.auth`;
    // vi.resetModules: 다음 import 때 session.js 를 새로 읽게 해 모듈 안 상태(메모리 세션·번호)를 초기화한다.
    // 기본 목: 보안 저장소는 비어 있고(get 거부), 쓰기·삭제는 성공한다
    beforeEach(() => {
      vi.resetModules();
      vi.clearAllMocks();
      localStorage.clear();
      secure.get.mockRejectedValue(new Error('missing'));
      secure.set.mockResolvedValue(undefined);
      secure.remove.mockResolvedValue(undefined);
    });
    afterEach(() => localStorage.clear());
    // 해당 앱의 session.js 를 불러온다. resetModules 뒤에 부르면 "앱을 새로 실행한 것" 과 같다
    const load = () => app === 'customer' ? import('./session.js') : import('../../../manager/src/api/session.js');
    it('access·refresh 토큰을 localStorage 에 남기지 않는다', async () => {
      const { session } = await load();
      await session.hydrate();
      session.set({ accessToken: 'access-secret', refreshToken: 'refresh-secret', expiresIn: 3600 });
      await vi.waitFor(() => expect(secure.set).toHaveBeenCalled());
      expect(localStorage.getItem(key)).toBeNull();
    });
    // 준비: localStorage 에 옛 평문 세션만 있다 (보안 저장소는 비어 있음). 확인: 그 세션을 쓰고, 보안 저장소에 쓰고, 평문은 지운다
    it('예전 설치의 평문 세션은 보안 저장소로 옮긴 뒤 지운다', async () => {
      localStorage.setItem(key, JSON.stringify({ accessToken: 'legacy', refreshToken: 'legacy-r' }));
      const { session } = await load();
      await session.hydrate();
      expect(session.accessToken()).toBe('legacy');
      expect(secure.set).toHaveBeenCalled();
      expect(localStorage.getItem(key)).toBeNull();
    });
    // 준비: 보안 저장소에 세션이 있고, remove 는 실패하게 한다 → 로그아웃 → 모듈을 새로 불러 "다시 실행".
    // 확인: 대기 표식이 남아 있어 보안 저장소의 옛 세션을 읽지 않는다 (세션 없음)
    it('보안 저장소 삭제가 실패해도 다음 실행에서 로그아웃한 세션을 되살리지 않는다', async () => {
      secure.get.mockResolvedValue({ value: JSON.stringify({ accessToken: 'stale', refreshToken: 'stale-r' }) });
      secure.remove.mockRejectedValue(new Error('device locked'));
      let { session } = await load();
      await session.hydrate();
      session.clear();
      await vi.waitFor(() => expect(secure.remove).toHaveBeenCalled());
      vi.resetModules();
      ({ session } = await load());
      await session.hydrate();
      expect(session.get()).toBeNull();
    });
    // 준비: 보안 저장소를 stored 변수로 흉내 내고, 평문 이전의 set 은 finishMigration() 을 부를 때까지 끝나지 않게 붙잡는다.
    // 이전이 진행 중일 때 로그아웃 → 이전을 늦게 끝낸다. 확인: 삭제가 이전 뒤에 실행돼 다음 실행에서 세션이 없다
    it('평문 이전 중 로그아웃해도 늦은 저장이 삭제를 추월하지 않는다', async () => {
      let stored = null; // 가짜 보안 저장소에 들어 있는 값
      let finishMigration;
      localStorage.setItem(key, JSON.stringify({ accessToken: 'legacy', refreshToken: 'legacy-r' }));
      secure.get.mockImplementation(async () => ({ value: stored }));
      secure.set.mockImplementation(({ value }) => new Promise((resolve) => {
        finishMigration = () => { stored = value; resolve(); };
      }));
      secure.remove.mockImplementation(async () => { stored = null; });
      let { session } = await load();
      const hydration = session.hydrate();
      await vi.waitFor(() => expect(finishMigration).toBeTypeOf('function'));
      session.clear();
      // 이전 쓰기가 끝나기 전에도 삭제가 먼저 끝날 수 있었던 순서를 재현한다.
      await new Promise((resolve) => setTimeout(resolve, 0));
      finishMigration();
      await hydration;
      // 대기 표식이 꺼질 때 = 줄 선 마지막 쓰기(로그아웃 삭제)까지 끝났을 때
      await vi.waitFor(() => expect(localStorage.getItem(`${key}.pending`)).toBeNull());
      vi.resetModules();
      ({ session } = await load());
      await session.hydrate();
      expect(session.get()).toBeNull();
    });
  });
}
