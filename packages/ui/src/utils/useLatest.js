// 순서 번호 훅. 쓰는 곳 예: usePolling 안(폴링 응답 걸러내기), 매니저 Availability 화면(저장 뒤 재조회).
import { useCallback, useMemo, useRef } from 'react';

/**
 * 늦게 도착한 옛 응답이 새 데이터를 덮지 않게 하는 순서 번호.
 * 자동 갱신(폴링·푸시)과 사용자 동작(취소·수락) 뒤 재조회가 섞이면, 먼저 나간 조회가 나중에 돌아와 최신 결과를 되돌릴 수 있다 (외부 검토 4차).
 *
 *   const latest = useLatest();
 *   const refresh = () => { const my = latest.next(); getX().then((d) => latest.is(my) && setData(d)).catch(() => {}); };
 *   const cancel = useAction(async () => { await cancelX(); latest.next(); await reload(); }); // 그 전에 나간 폴링은 버려진다
 *
 * @returns {{ next: () => number, is: (n: number) => boolean }}
 */
export function useLatest() {
  const seq = useRef(0); // 마지막으로 발급한 번호 — 화면에 보이는 값이 아니라 다시 그릴 필요가 없다
  const next = useCallback(() => ++seq.current, []); // 새 번호 발급 — 그 전에 받은 번호는 모두 옛 것이 된다
  const is = useCallback((n) => n === seq.current, []); // n 이 아직 마지막 번호인가 (응답을 반영해도 되는가)
  return useMemo(() => ({ next, is }), [next, is]); // 같은 객체 — 화면이 useEffect deps 에 넣어도 매 렌더 재구독되지 않는다 (검수 3차)
}
