// 화면이 api 함수를 부를 때 쓰는 공통 훅. 로딩·오류·재시도를 한 곳에서 처리한다.
//   const { data, error, loading, reload, setData } = useAsync(() => getHome(), []);
//   if (error) return <ScreenError onRetry={reload} />;
//   if (!data) return <ScreenLoading />;
// - deps 가 바뀌면 다시 부른다 (예: [id]).
// - 늦게 도착한 옛 응답은 버린다 (화면이 바뀌었거나 reload 를 눌렀을 때).
// - setData 로 화면이 낙관적 갱신을 할 수 있다 (체크리스트 토글 등).
// - reload 는 처음처럼(로딩 → 결과) — ScreenError 의 '다시 시도' 용. 동작 뒤 목록만 새로 받을 때는 refresh:
//   받아 둔 화면을 그대로 둔 채 새 값으로 바꾸고, 실패하면 화면은 그대로 두고 Promise 가 reject 된다 (자체 점검 #48 —
//   reload 는 data 를 버리고 Promise 도 돌려주지 않아, 토글 실패 뒤 재조회가 설정 화면 전체를 로딩·오류로 덮었다)
import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * @template T
 * @param {() => Promise<T>} load api 함수를 부르는 화살표 함수
 * @param {unknown[]} deps 이 값이 바뀌면 다시 부른다
 * @returns {{ data: T|undefined, error: unknown, loading: boolean, reload: () => void, refresh: () => Promise<T>, setData: (next: T | ((prev: T) => T)) => void }}
 */
export function useAsync(load, deps) {
  // deps 배열은 렌더마다 새 배열이라 내용을 문자열로 바꿔 비교한다 — 그래서 deps 에는 JSON 으로 바꿀 수 있는 값(숫자·문자열 등)을 넣는다
  const depsKey = JSON.stringify(deps);
  const [tick, setTick] = useState(0); // reload 가 1씩 올린다 — 바뀌면 아래 효과가 다시 돈다
  // 마지막으로 받은 결과. 어느 tick·depsKey 로 받은 것인지 같이 적어 두어, 지금 값과 다르면 아직 로딩 중으로 본다 (아래 loading)
  const [result, setResult] = useState({ tick: -1, depsKey: null, data: undefined, error: null });

  // 정리 함수(다음 실행 전·화면이 사라질 때 불린다)에서 alive 를 false 로 바꿔, 그 뒤에 도착한 옛 요청의 응답은 버린다.
  useEffect(() => {
    let alive = true;
    new Promise((resolve) => resolve(load())) // load 가 동기적으로 던져도 reject 로 받는다
      .then(
        (data) => alive && setResult({ tick, depsKey, data, error: null }),
        (error) => alive && setResult({ tick, depsKey, data: undefined, error }),
      );
    return () => {
      alive = false;
    };
    // load 는 매 렌더마다 새 화살표 함수라 deps 에 넣지 않는다. 다시 부르는 조건은 deps 와 reload 뿐이다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick, depsKey]);

  // refresh 는 한 번 만든 함수를 계속 쓰므로(useCallback 의존값 없음) 최신 load·depsKey 를 ref 로 읽는다.
  // refreshSeq 는 refresh 가 겹쳐 불렸을 때 마지막 것만 반영하려는 순서 번호다.
  const loadRef = useRef(load);
  const depsKeyRef = useRef(depsKey);
  const refreshSeq = useRef(0);
  useEffect(() => {
    loadRef.current = load; // 렌더 중 ref 쓰기는 lint 가 막아 효과에서 (usePolling 과 같다)
    depsKeyRef.current = depsKey;
  });

  const loading = result.tick !== tick || result.depsKey !== depsKey; // 첫 조회 중이거나, reload·deps 변경 뒤 새 결과가 아직 없다
  const reload = useCallback(() => setTick((t) => t + 1), []);
  // 실패하면 await 가 그대로 던져 Promise 가 reject 된다 — data·error 는 건드리지 않는다
  const refresh = useCallback(async () => {
    const my = ++refreshSeq.current;
    const key = depsKeyRef.current;
    const data = await loadRef.current();
    // 더 늦게 시작한 refresh 가 있거나 그 사이 다른 대상(deps)으로 바뀌었으면 버린다
    if (my === refreshSeq.current) setResult((r) => (r.depsKey === key ? { ...r, data, error: null } : r));
    return data;
  }, []);
  // 데이터가 왔다는 뜻이므로 error 도 지운다 — 첫 조회가 실패한 뒤 폴링·푸시 갱신이 성공해도 오류 화면이 남지 않게 (검수 3차)
  const setData = useCallback((next) => setResult((r) => ({ ...r, data: typeof next === 'function' ? next(r.data) : next, error: null })), []);

  // 로딩 중에는 옛 결과를 숨긴다 — id 가 바뀐 직후 이전 대상의 데이터·오류가 잠깐 보이지 않게
  return { data: loading ? undefined : result.data, error: loading ? null : result.error, loading, reload, refresh, setData };
}
