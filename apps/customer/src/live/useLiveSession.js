// 라이브 세션 훅 (시청). 화면은 이 훅의 반환값만 쓰고 WebRTC/LiveKit API 를 직접 부르지 않는다 (CLAUDE.md §1).
// 연결 코드는 liveClient.js(서버 세션·토큰·녹화본) 와 roomConnector.js(LiveKit) 에 있고, 이 훅은 그 상태를 React 에 묶어 줄 뿐이다.
import { useEffect, useSyncExternalStore } from 'react';
import { liveClient } from './liveClient.js';

/**
 * @param {string} [bookingId] 시청할 예약 id. 없으면 DEV 에서만 mock 세션이고 프로덕션은 오류
 *   (두 시청 화면은 booking 이 없으면 먼저 /reservations 로 보낸다)
 * @param {{ replay?: boolean }} [opts] replay=true 면 녹화본 재생 (완료 탭 "녹화 영상 보기")
 * @returns {{
 *   status: import('./liveClient.js').LiveStatus,   connecting = 카메라 연결 대기, live/paused = 실시간 또는 녹화본 재생 중/멈춤
 *   mode: 'live'|'replay',
 *   session: object|null,
 *   error: unknown,
 *   muted: boolean,
 *   videoRef: (el: HTMLVideoElement|null) => void,   <VideoSurface ref={videoRef}> 에 넘긴다
 *   toggleMute: () => void, capture: () => void,
 *   seek: (deltaSeconds: number) => void, seekRatio: (ratio: number) => void, back15: () => void, forward15: () => void, togglePause: () => void,
 *   leave: () => void, retry: () => void,   retry = 세션 조회에 실패했을 때 다시 join
 * }}
 * 예: ServiceLivePortrait·ServiceLiveLandscape 가 주소의 ?booking=…&replay=1 을 읽어 넘긴다
 */
export function useLiveSession(bookingId, { replay = false } = {}) {
  // useSyncExternalStore: React 밖의 저장소(liveClient)를 구독해, 상태 객체가 바뀔 때마다 이 화면을 다시 그린다
  const { status, mode, session, error, muted } = useSyncExternalStore(liveClient.subscribe, liveClient.getState);

  useEffect(() => {
    liveClient.join(bookingId, { replay });
    return () => liveClient.release(); // 화면이 사라지면 나간다(뒤로가기 포함). 세로↔가로 전환은 곧 다시 join 하므로 유지된다
  }, [bookingId, replay]);

  return {
    status,
    mode,
    session,
    error,
    muted,
    videoRef: liveClient.attachVideo, // ref callback: 마운트 시 엘리먼트, 언마운트 시 null
    toggleMute: liveClient.toggleMute,
    capture: liveClient.capture,
    seek: liveClient.seek,
    seekRatio: liveClient.seekRatio,
    back15: liveClient.back15,
    forward15: liveClient.forward15,
    togglePause: liveClient.togglePause,
    leave: liveClient.leave,
    retry: () => liveClient.join(bookingId, { replay }),
  };
}
