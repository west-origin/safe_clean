// 수리 예약 초안 사진의 24시간 만료를 화면이 '지금' 알아보게 하는 훅. 쓰는 곳: 증상 상세(RepairDetail)·확인 화면(RepairConfirm).
// 만료 판정은 repairFlow.js 의 freshPhotos(기기 시계)다. 이 훅은 그 판정을 다시 할 때를 정한다 —
//   ① 남은 사진 중 가장 먼저 만료되는 순간(타이머) ② 앱이 백그라운드에서 돌아온 순간(visibilitychange).
// 예전에는 화면이 다시 그려질 때만 재서, 연 채로 24시간을 넘기면 확인 화면 요약은 만료된 사진까지 세고
// 증상 상세는 글자를 입력할 때 사진을 안내 없이 감췄다 (합친 38건 검토 R10).
// ②가 따로 있는 이유: 앱이 백그라운드에 있는 동안 WebView 는 타이머를 멈추거나 늦게 돌려, 돌아온 뒤에도 ①이 한참 늦을 수 있다.
// 무엇을 할지(초안 정리·만료 안내·증상 상세로 보내기)는 부르는 화면이 expired 를 보고 effect 에서 정한다 — 렌더 중에는 상태를 바꾸지 않는다.
import { useEffect, useReducer } from 'react';
import { freshPhotos, PHOTO_TTL_MS } from './repairFlow.js';

/**
 * @param {{ uploadedAt?: number }[]|undefined} photos 초안의 photos
 * @returns {{ photos: object[], expired: boolean }} photos = 지금 쓸 수 있는 사진(화면에 그릴 것), expired = 초안에 24시간 지난 사진이 있다
 */
export function useFreshPhotos(photos) {
  // 다시 재야 할 때 화면을 다시 그리게 하는 숫자. 값 자체는 쓰지 않고, 바뀔 때마다 아래 타이머 effect 를 다시 돌린다
  const [tick, rerender] = useReducer((n) => n + 1, 0);
  const fresh = freshPhotos(photos);
  const expired = fresh.length !== (photos ?? []).length;
  // 남은 사진 중 가장 먼저 만료되는 시각(밀리초). 올린 시각이 없는 사진(이 규칙 전 초안)은 알 수 없어 뺀다. 잴 사진이 없으면 0
  const nextExpiry = fresh.reduce((min, p) => (p.uploadedAt && (!min || p.uploadedAt + PHOTO_TTL_MS < min) ? p.uploadedAt + PHOTO_TTL_MS : min), 0);

  // ① 그 시각에 다시 그린다. tick 이 deps 에 있는 이유: 타이머가 경계보다 조금 일찍 깨어나 아직 만료가 아니면
  // nextExpiry 가 그대로라 effect 가 다시 돌지 않는다 — 다시 그릴 때마다 남은 시간으로 새 타이머를 건다
  useEffect(() => {
    if (!nextExpiry) return undefined;
    const timer = setTimeout(rerender, Math.max(0, nextExpiry - Date.now()));
    return () => clearTimeout(timer);
  }, [nextExpiry, tick]);

  // ② 앱이 다시 앞으로 오면 바로 다시 잰다 (숨겨질 때는 하지 않는다)
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState !== 'hidden') rerender();
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, []);

  return { photos: fresh, expired };
}
