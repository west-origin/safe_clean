// 짧은 성공 안내(토스트) 저장소 — 화면은 showToast('저장했어요') 만 부르고, <ToastHost/> 가 앱 루트에서 그린다 (코드에서 정함, 검증 검토: 저장·취소가 됐는지 알 길이 없었다).
// Figma 에 토스트가 없어 문구는 짧게, 3초 뒤 사라진다. 오류는 토스트가 아니라 ActionError/ScreenError.
import { useSyncExternalStore } from 'react';

// 아래 모듈 변수들이 앱 전체에 하나뿐인 저장소다. React state 가 아니라서 컴포넌트 밖(동작 함수 안 등)에서도
// showToast 를 부를 수 있고, 화면을 옮겨도 떠 있는 토스트가 이어진다.
const DURATION_MS = 3000;
let current = null; // { id, message }
let timer = null; // 3초 뒤 닫는 setTimeout 번호
const listeners = new Set(); // 값이 바뀌면 다시 그려야 하는 구독자 (useToast 를 쓰는 ToastHost)
let seq = 0; // 토스트마다 새 id — 같은 문구를 다시 띄워도 ToastHost 의 key 가 달라 버튼을 새로 그리고 나타나는 애니메이션이 다시 돈다

/** 구독자 모두에게 값이 바뀌었다고 알린다 */
function emit() {
  listeners.forEach((fn) => fn());
}

// 토스트를 띄운다. 이미 떠 있으면 새 문구로 바로 바꾸고 3초를 처음부터 다시 잰다. 빈 문구는 무시한다.
/** @param {string} message 한 줄 문구 */
export function showToast(message) {
  if (!message) return;
  current = { id: ++seq, message };
  if (timer) clearTimeout(timer); // 앞 토스트의 타이머가 새 토스트를 일찍 닫지 않게
  timer = setTimeout(() => {
    current = null;
    timer = null;
    emit();
  }, DURATION_MS);
  emit();
}

/** 토스트를 바로 닫는다. ToastHost 의 토스트 버튼을 누르면 불린다 (ToastHost 테스트도 정리할 때 부른다) */
export function hideToast() {
  if (timer) clearTimeout(timer);
  timer = null;
  current = null;
  emit();
}

// useSyncExternalStore 에 넘길 두 함수. 이 훅은 React 바깥에 있는 값(여기서는 current)을 구독해
// 값이 바뀌면(emit) 컴포넌트를 다시 그린다. subscribe 는 구독 해제 함수를 돌려준다.
const subscribe = (fn) => {
  listeners.add(fn);
  return () => listeners.delete(fn);
};
const snapshot = () => current; // 지금 값. 바뀔 때만 새 객체라 React 가 달라졌는지 비교할 수 있다

// 지금 떠 있는 토스트 { id, message } 또는 null. 세 번째 인자는 서버 렌더링 때 쓰는 값이라 같은 함수를 넘긴다
/** ToastHost 가 쓴다 */
export function useToast() {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}
