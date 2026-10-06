// 남은 시간 카운트다운 훅과 도우미 (secondsUntil, mmss).
// 쓰는 곳 예: 인증번호 재전송 대기(고객 ProfileInfo), 매니저 콜 만료 표시(CallList).
// 남은 초를 React state 가 아니라 모듈의 Map 에 두고 useSyncExternalStore 로 구독한다.
// (useSyncExternalStore: React 바깥 값을 subscribe(바뀌면 알려 줄 함수 등록)·getSnapshot(지금 값) 두 함수로 읽는 훅)
import { useCallback, useSyncExternalStore } from 'react';

// 올림이라 0.1초가 남아도 1 로 보인다. target 이 없으면 0
/** 남은 초 계산 (렌더 밖 — Date.now 는 여기서만) */
function remainingOf(target) {
  return target ? Math.max(0, Math.ceil((target - Date.now()) / 1000)) : 0;
}

/** 구독 중인 시각별 마지막 값 — getSnapshot 은 이 값을 돌려준다. 렌더 도중 초가 바뀌어도 같은 값이라 React 의 "getSnapshot should be cached" 경고·재렌더가 없다 (검수 4차). 1초마다 틱이 갱신한다 */
const current = new Map();
const subscribers = new Map(); // 같은 시각을 보는 구독자 수 — 여럿이 같은 시각을 구독해도 마지막 구독자가 떠날 때만 값을 지운다

/**
 * 어떤 시각(ms)까지 남은 초. 1초마다 갱신되고 0 이 되면 멈춘다.
 * 인증번호 재전송 대기(서버 60초 제한, 검증 검토)·콜 만료 카운트다운에 쓴다.
 * @param {number|string|null|undefined} until 밀리초 시각 또는 ISO 문자열. 없으면 0
 * @returns {number} 남은 초 (0 이상)
 */
export function useCountdown(until) {
  const target = until ? (typeof until === 'number' ? until : Date.parse(until)) : 0; // 밀리초 숫자로 맞춘다 (Map 키)
  // target 이 바뀔 때만 새 함수가 되어 React 가 옛 구독을 풀고 다시 구독한다
  const subscribe = useCallback(
    (onChange) => {
      if (!target || remainingOf(target) <= 0) return () => {}; // 목표가 없거나 이미 지났으면 타이머를 만들지 않는다
      current.set(target, remainingOf(target));
      subscribers.set(target, (subscribers.get(target) ?? 0) + 1);
      // 1초마다 다시 계산해 초가 바뀐 때만 알린다(같으면 다시 그리지 않는다). 0 이 되면 타이머를 멈춘다
      const timer = setInterval(() => {
        const next = remainingOf(target);
        if (next !== current.get(target)) {
          current.set(target, next);
          onChange();
        }
        if (next <= 0) clearInterval(timer);
      }, 1000);
      // 구독 해제(화면이 사라지거나 target 이 바뀜): 이 구독의 타이머를 멈추고 구독자 수를 줄인다
      return () => {
        clearInterval(timer);
        if ((subscribers.get(target) ?? 1) <= 1) {
          subscribers.delete(target);
          current.delete(target); // 같은 시각을 보는 마지막 구독자가 떠날 때만 지운다
        } else subscribers.set(target, subscribers.get(target) - 1);
      };
    },
    [target],
  );
  // 구독 전(첫 렌더)이나 구독하지 않은 경우(이미 지남)에는 Map 에 값이 없어 직접 계산한다
  const snapshot = useCallback(() => current.get(target) ?? remainingOf(target), [target]);
  return useSyncExternalStore(subscribe, snapshot, snapshot); // 세 번째 인자는 서버 렌더링 때 쓰는 값 — 같은 함수를 넘긴다
}

/** 만료 시각(ISO)까지 남은 초 — 목록에서 카드마다 계산할 때 (렌더 밖 함수라 Date.now 를 써도 된다). 지났으면 0 */
export const secondsUntil = (until) => remainingOf(until ? (typeof until === 'number' ? until : Date.parse(until)) : 0);

/** 초 → "m:ss" (예: 59 → "0:59", 125 → "2:05") */
export const mmss = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
