// 테스트 파일마다 그 파일보다 먼저 실행되는 공통 준비 (vitest.config.js 의 setupFiles).
// ① DOM 검사용 expect 확장 ② Testing Library 대기 시간 ③ 테스트마다 DOM 정리 ④ localStorage 대체품.
// toBeInTheDocument()·toHaveTextContent() 같은 DOM 전용 expect 를 쓸 수 있게 한다
import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// findBy*·waitFor 의 기본 대기 1초는 여러 단계를 잇는 흐름(완료 → 영상 정지 → 시트 닫기 → 이동)에 빠듯해,
// 다른 작업과 CPU 를 나눌 때 '다음 화면이 안 떴다' 로 간헐 실패했다. 정말 안 뜨는 회귀는 3초 뒤에도 실패한다 (자체 점검 후속 재검토)
configure({ asyncUtilTimeout: 3000 });

// Vitest 는 globals 를 켜지 않으면 Testing Library 의 자동 cleanup 이 동작하지 않는다. 테스트마다 DOM 을 비운다.
afterEach(() => cleanup());

// Node 22+ 는 globalThis.localStorage 를 (실험 기능이 꺼진) undefined 로 미리 갖고 있어 jsdom 의 localStorage 가 덮어쓰이지 않는다.
// 앱 세션·가입 초안이 localStorage 를 쓰므로 테스트에서는 같은 API 의 메모리 저장소를 둔다 (테스트마다 비운다).
if (typeof globalThis.localStorage === 'undefined' || globalThis.localStorage === null) {
  const store = new Map();
  // 브라우저 localStorage 와 같은 메서드 이름·동작(값은 항상 문자열로 저장, 없는 키는 null)만 흉내 낸다
  const memoryStorage = {
    getItem: (k) => (store.has(String(k)) ? store.get(String(k)) : null),
    setItem: (k, v) => void store.set(String(k), String(v)),
    removeItem: (k) => void store.delete(String(k)),
    clear: () => store.clear(),
    key: (i) => [...store.keys()][i] ?? null,
    get length() {
      return store.size;
    },
  };
  Object.defineProperty(globalThis, 'localStorage', { value: memoryStorage, configurable: true, writable: true });
}
afterEach(() => globalThis.localStorage.clear()); // 위 대체품이든 jsdom 것이든, 앞 테스트의 세션·초안이 다음 테스트로 새지 않게
