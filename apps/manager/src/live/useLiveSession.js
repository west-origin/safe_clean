// 라이브 세션 훅 (서버 세션 제어 + 바디캠 프리뷰 시청 — 휴대폰은 송출하지 않는다).
// 화면은 이 훅의 반환값만 쓰고 WebRTC/LiveKit API 를 직접 부르지 않는다 (CLAUDE.md §1).
// 연결 코드는 liveClient.js(서버 세션 + 프리뷰) 와 roomConnector.js(LiveKit) 에 있고, 이 훅은 그 상태를 React 에 묶어 줄 뿐이다.
// 쓰는 화면 예: Job/InProgress(프리뷰·일시정지·완료), Job/Paused(다시 시작).
import { useSyncExternalStore } from 'react';
import { liveClient } from './liveClient.js';

/**
 * @returns {{
 *   status: import('./liveClient.js').LiveStatus,   connecting = 바디캠 연결 대기 중
 *   jobId: string|null,
 *   startedAt: number|null,
 *   cameraOnline: boolean, cameraBusy: boolean, held: boolean,   held = 서버가 '매니저가 영상을 멈춘 채' 로 안다 (재개할 때까지 영상을 막아 둔다)
 *     cameraBusy = 바디캠을 껐다가 다시 켜야 한다 (§8.1)
 *   error: unknown,
 *   videoRef: (el: HTMLVideoElement|null) => void,   바디캠 프리뷰 <video>
 *   start: (jobId: string) => Promise<void>,
 *   pause: (jobId?: string) => Promise<void>, resume: (jobId?: string) => Promise<void>,   실패하면 reject — 화면은 그때 이동하지 않는다. jobId 는 재진입(앱 재실행) 대비
 *   stop: (jobId?: string) => Promise<void>,
 * }}
 */
export function useLiveSession() {
  // useSyncExternalStore = React 밖의 저장소(liveClient)를 구독하고, 상태가 바뀌면 이 훅을 쓰는 화면을 다시 그린다
  const { status, jobId, startedAt, cameraOnline, cameraBusy, held, error } = useSyncExternalStore(liveClient.subscribe, liveClient.getState);
  // 함수들은 liveClient 의 것을 그대로 넘긴다. videoRef 는 <VideoSurface ref={videoRef}> 처럼 ref 로 쓴다
  return {
    status,
    jobId,
    startedAt,
    cameraOnline,
    cameraBusy,
    held,
    error,
    videoRef: liveClient.attachVideo,
    start: liveClient.start,
    pause: liveClient.pause,
    resume: liveClient.resume,
    stop: liveClient.stop,
  };
}
