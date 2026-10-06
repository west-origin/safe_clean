// 작업 경과 시간 "HH:MM:SS" 훅(useElapsed)과 초 → 문자열 도우미(hms).
// 쓰는 곳 예: 매니저 홈의 진행 중 카드, JobInProgress 화면. useCountdown 과 같은 방식으로
// 값을 모듈의 Map 에 두고 useSyncExternalStore(React 바깥 값을 구독하는 훅)로 1초마다 다시 그린다.
import { useCallback, useSyncExternalStore } from 'react';

/** HH:MM:SS */
export function hms(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds)); // 음수는 0, 소수점 아래는 버린다 (예: 3725 → "01:02:05")
  const two = (n) => String(n).padStart(2, '0');
  return `${two(Math.floor(s / 3600))}:${two(Math.floor((s % 3600) / 60))}:${two(s % 60)}`;
}

// 서버가 준 시작 시각(startedAt)부터 지금까지의 초에서 누적 중단 초를 뺀다.
// "지금" 은 기기 시계(Date.now)라 기기 시계가 틀리면 그만큼 어긋난다
function elapsedOf(startedAt, pausedSeconds) {
  return Math.max(0, Math.floor((Date.now() - Date.parse(startedAt)) / 1000) - (pausedSeconds ?? 0));
}

/** 구독 중인 (startedAt, pausedSeconds) 별 마지막 문자열 — useCountdown 과 같은 이유 (검수 4차) */
const current = new Map();
const subscribers = new Map(); // 같은 키를 보는 구독자 수 — 여럿이 같은 키를 구독해도 마지막 구독자가 떠날 때만 값을 지운다
const keyOf = (startedAt, pausedSeconds) => `${startedAt}|${pausedSeconds ?? 0}`; // 시작 시각과 중단 초가 모두 같아야 같은 값

/**
 * 작업 경과 시간 "HH:MM:SS" — 서버 startedAt(ISO)·pausedSeconds(누적 중단 초)로 1초마다 계산한다. 일시정지 중엔 멈춘다(서버 문자열 그대로).
 * 매니저 홈 카드·진행 중 화면이 같은 계산을 쓴다 (검증 검토: 홈은 30초 폴링 문자열이라 30초씩 뛰었다).
 * @param {string|null|undefined} startedAt ISO
 * @param {number} [pausedSeconds]
 * @param {string} [initial] 서버가 준 첫 화면 값 (startedAt 이 없을 때·멈춰 있을 때)
 * @param {boolean} [paused]
 * @returns {string} "HH:MM:SS". 멈춰 있거나 startedAt 이 없으면 initial, 그것도 없으면 빈 문자열
 */
export function useElapsed(startedAt, pausedSeconds, initial, paused = false) {
  const active = Boolean(startedAt) && !paused; // 시작했고 일시정지가 아닐 때만 1초 타이머를 돌린다
  const key = keyOf(startedAt, pausedSeconds);
  // startedAt·pausedSeconds·paused 중 하나가 바뀌면 새 함수가 되어 React 가 옛 구독을 풀고 다시 구독한다
  const subscribe = useCallback(
    (onChange) => {
      if (!active) return () => {};
      // 구독 시점 값을 먼저 넣고, 1초마다 다시 계산해 문자열이 바뀐 때만 알린다 (useCountdown 과 달리 스스로 멈추지 않는다)
      current.set(key, hms(elapsedOf(startedAt, pausedSeconds)));
      subscribers.set(key, (subscribers.get(key) ?? 0) + 1);
      const timer = setInterval(() => {
        const next = hms(elapsedOf(startedAt, pausedSeconds));
        if (next !== current.get(key)) {
          current.set(key, next);
          onChange();
        }
      }, 1000);
      // 구독 해제(화면이 사라지거나 값이 바뀜): 이 구독의 타이머를 멈추고 구독자 수를 줄인다
      return () => {
        clearInterval(timer);
        if ((subscribers.get(key) ?? 1) <= 1) {
          subscribers.delete(key);
          current.delete(key); // 같은 시각을 보는 마지막 구독자가 떠날 때만 지운다
        } else subscribers.set(key, subscribers.get(key) - 1);
      };
    },
    [active, key, startedAt, pausedSeconds],
  );
  // 돌고 있으면 Map 의 값(구독 전이면 직접 계산), 멈춰 있으면 서버가 준 initial 문자열을 그대로 보여 준다
  const snapshot = useCallback(() => (active ? (current.get(key) ?? hms(elapsedOf(startedAt, pausedSeconds))) : (initial ?? '')), [active, key, startedAt, pausedSeconds, initial]);
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
