// 버튼 동작(예약 생성·취소·일시정지 …)의 진행 중·실패 상태를 다루는 훅. useAsync 가 "화면 진입 시 조회" 용이라면 이것은 "탭 후 요청" 용이다.
// 진행 중에는 다시 부를 수 없어(loading) 더블 탭으로 같은 요청이 두 번 가지 않는다. 실패는 error 로 돌려주고 던지지 않는다.
import { useCallback, useRef, useState } from 'react';

/**
 * @template {any[]} A
 * @template R
 * @param {(...args: A) => Promise<R>} fn 실행할 동작
 * @returns {{ run: (...args: A) => Promise<R|undefined>, loading: boolean, error: Error|null, reset: () => void }}
 *   run 은 성공하면 결과를, 실패하면 undefined 를 돌려준다(error 에 담긴다). 진행 중에 다시 부르면 아무것도 하지 않는다.
 */
export function useAction(fn) {
  const [state, setState] = useState({ loading: false, error: null });
  // state 는 다음 렌더에야 바뀌므로, 같은 렌더 안에서 두 번 눌러도 막으려면 ref 로 곧바로 "실행 중" 을 표시한다.
  const running = useRef(false);
  const generation = useRef(0); // run 시작·reset 마다 +1 — reset 전에 시작한 실행의 실패 문구가 새로 연 시트에 뜨지 않게
  // useCallback: 의존값([fn])이 같으면 같은 함수를 돌려준다.
  // 화면은 보통 fn 을 렌더마다 새로 만들므로 run 도 그때마다 새로 만들어져 늘 최신 fn 을 부른다.
  const run = useCallback(
    async (...args) => {
      if (running.current) return undefined;
      running.current = true;
      const my = ++generation.current; // 이 실행의 번호 — 끝났을 때 번호가 바뀌어 있으면 그 사이 reset 된 것이다
      setState({ loading: true, error: null });
      try {
        const result = await fn(...args);
        setState({ loading: false, error: null });
        return result;
      } catch (error) {
        // 실패는 던지지 않고 error 로 보여 준다. reset 뒤에 끝난 옛 실행이면 문구를 버린다
        setState({ loading: false, error: my === generation.current ? error : null });
        return undefined;
      } finally {
        running.current = false; // 성공·실패 어느 쪽이든 다음 탭을 받는다
      }
    },
    [fn],
  );
  // reset: 실패 문구를 지운다 — 화면이 시트를 열거나 닫을 때 이전 실패 문구가 남지 않게 부른다 (예: 매니저 CallList 의 거절 시트)
  // 진행 중에 부르면 문구만 지우고 스피너는 둔다 — 예전에는 버튼이 풀려 보이는데 눌러도 반응이 없었다 (자체 점검 #49)
  const reset = useCallback(() => {
    generation.current++;
    setState(() => ({ loading: running.current, error: null }));
  }, []);
  return { run, loading: state.loading, error: state.error, reset };
}
