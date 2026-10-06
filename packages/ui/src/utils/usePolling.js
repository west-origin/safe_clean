// 목록 화면의 조용한 갱신: 일정 간격 + (선택) 푸시 구독 + 앱이 다시 보일 때 즉시. 화면이 숨겨져 있으면(백그라운드 탭·앱 전환) 돌지 않는다.
//   const { data, error, reload, refresh, setData, invalidate } = usePolling(() => getHome(), [], { interval: 30_000, subscribe: onPushReceived });
//   const cancel = useAction(async () => { await cancelX(); invalidate(); await refresh().catch(() => {}); }); // 그 전에 나간 폴링 응답은 버린다
//   (동작 뒤에는 reload 가 아니라 refresh — reload 는 Promise 를 돌려주지 않고 화면을 로딩으로 덮는다, useAsync 설명. 예: Reservations 의 취소)
// useAsync 위에 얹은 것이라 반환값도 같다(+ invalidate). 첫 조회 실패 뒤 폴링이 성공하면 오류 화면이 사라진다(useAsync.setData).
// 화면마다 손으로 반복하던 폴링 코드와 백그라운드 폴링·복귀 갱신 누락을 한 곳으로 모았다 (검증 검토).
// 쓰는 곳 예: 매니저 홈·콜 목록, 고객 내 예약.
import { useEffect, useRef } from 'react';
import { useAsync } from './useAsync.js';
import { useLatest } from './useLatest.js';

/**
 * @template T
 * @param {() => Promise<T>} load api 함수를 부르는 화살표 함수 (useAsync 와 같다)
 * @param {unknown[]} deps 이 값이 바뀌면 처음부터 다시 부른다
 * @param {object} [opts]
 * @param {number} [opts.interval=30000] 갱신 간격(ms). 0 이면 간격 갱신 없음
 * @param {(fn: () => void) => (() => void)} [opts.subscribe] 외부 신호(푸시 수신 등) 구독 — 해제 함수를 돌려준다
 * @param {(error: unknown) => void} [opts.onError] 조용한 갱신이 실패했을 때 (기본은 무시 — 다음 갱신이 메운다). 예: 대상이 사라진 404·409 를 화면에 올릴 때
 * @returns {ReturnType<typeof useAsync> & { invalidate: () => void }}
 */
export function usePolling(load, deps, { interval = 30_000, subscribe, onError } = {}) {
  const query = useAsync(load, deps);
  const { setData } = query; // useAsync 가 한 번만 만드는 함수라 아래 효과 deps 에 넣어도 다시 구독되지 않는다
  const latest = useLatest(); // 폴링 요청마다 번호를 받아, 더 새 요청이나 invalidate 뒤에 온 옛 응답을 버린다
  const loadRef = useRef(load);
  const onErrorRef = useRef(onError); // 화면이 매 렌더 새 화살표 함수를 넘겨도 다시 구독하지 않게 ref 로 든다
  useEffect(() => {
    onErrorRef.current = onError;
    loadRef.current = load; // 효과는 deps 가 바뀔 때만 다시 도는데, 폴링은 늘 최신 렌더의 load 를 써야 한다 (검수 4차: 매 렌더 새 객체라 효과가 잡은 옛 load 를 썼다). 렌더 중 ref 쓰기는 lint 가 막아 효과에서
  });
  // 간격 타이머·외부 신호·화면 복귀 이벤트를 한 번에 걸고, 정리 함수에서 모두 푼다
  useEffect(() => {
    // deps(대상)가 바뀌었다 — 옛 대상으로 나가 있던 폴링 응답은 도착해도 버린다 (예: /job/A 를 보다 /job/B 로 바뀐 뒤 A 의 응답이 B 화면을 덮었다)
    latest.next();
    // visibilityState 는 탭이 뒤로 가거나 앱이 백그라운드면 'hidden'. document 가 없는 환경이면 보인다고 본다
    const visible = () => typeof document === 'undefined' || document.visibilityState !== 'hidden';
    // 조용한 갱신: 화면을 로딩으로 덮지 않고 성공한 값만 setData 로 바꾼다 (오류 화면이던 경우도 지워진다)
    const refresh = () => {
      if (!visible()) return; // 화면이 안 보이면 요청하지 않는다 — 배터리·서버 부하
      const my = latest.next();
      // then 안에서 부르므로 load 가 동기적으로 던져도 아래 catch 로 간다
      Promise.resolve()
        .then(() => loadRef.current())
        .then((d) => latest.is(my) && setData(d))
        .catch((e) => latest.is(my) && onErrorRef.current?.(e)); // 기본은 무시 — 다음 갱신이 메운다
    };
    const timer = interval > 0 ? setInterval(refresh, interval) : null;
    // subscribe 는 deps 에 들어 있어, 렌더마다 새 함수를 넘기면 매번 다시 구독된다.
    // 푸시를 받는 화면들은 모듈 함수 onPushReceived 를 넘긴다
    const off = subscribe ? subscribe(refresh) : null;
    const onVisible = () => visible() && refresh(); // 돌아오자마자 한 번
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisible);
    return () => {
      if (timer) clearInterval(timer);
      if (off) off();
      if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisible);
    };
    // load 는 매 렌더 새 함수라 ref 로 최신 것을 쓴다. 다시 구독하는 조건은 interval·subscribe·deps 뿐
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [interval, subscribe, setData, latest, JSON.stringify(deps)]);
  // invalidate = 번호만 새로 받는다 → 지금 나가 있는 폴링 응답은 도착해도 버려진다 (사용자 동작 뒤 되돌림 방지)
  return { ...query, invalidate: latest.next };
}
