// 라이브 세션 훅 (서버 세션 제어 + 미리보기). 영상 출처는 서버 설정: app(기본) = 휴대폰 카메라 송출, ingress = 바디캠 프리뷰 시청.
// 화면은 이 훅의 반환값만 쓰고 WebRTC/LiveKit/카메라 API 를 직접 부르지 않는다 (CLAUDE.md §1·§10).
// 연결 코드는 liveClient.js(서버 세션 + 송출/프리뷰), sources/(영상 입력), roomConnector.js(LiveKit) 에 있고, 이 훅은 그 상태를 React 에 묶어 줄 뿐이다.
// 쓰는 화면 예: Job/Preparing(카메라 미리보기·청소 시작), Job/InProgress(미리보기·일시정지·완료), Job/Paused(다시 시작).
import { useSyncExternalStore } from 'react';
import { liveClient } from './liveClient.js';

/**
 * @returns {{
 *   status: import('./liveClient.js').LiveStatus,   connecting = 카메라 연결 대기 중
 *   jobId: string|null,
 *   startedAt: number|null,
 *   cameraOnline: boolean, cameraBusy: boolean, held: boolean,   held = 서버가 '매니저가 영상을 멈춘 채' 로 안다 (재개할 때까지 영상을 막아 둔다)
 *     cameraBusy = 바디캠을 껐다가 다시 켜야 한다 (바디캠 모드 §8.1)
 *   source: 'app'|'ingress'|null,   영상 출처 (app = 휴대폰 카메라 송출, §8.0)
 *   camera: 'off'|'starting'|'ready'|'error',   휴대폰 카메라 상태 (app 모드)
 *   cameraError: 'denied'|'notFound'|'busy'|'failed'|null,   카메라를 켜지 못한 이유
 *   reconnecting: boolean,   송출 연결이 잠깐 끊겨 다시 붙는 중
 *   error: unknown,
 *   videoRef: (el: HTMLVideoElement|null) => void,   미리보기 <video> (app = 내 카메라, ingress = 바디캠)
 *   prepare: (jobId: string) => Promise<void>,   청소 준비 화면 진입·'카메라 다시 연결' — app 이면 카메라 권한을 묻고 미리보기를 켠다
 *   releaseCamera: () => void,   청소를 시작하지 않고 준비 화면을 떠날 때 카메라를 끈다
 *   start: (jobId: string) => Promise<void>,
 *   pause: (jobId?: string) => Promise<void>, resume: (jobId?: string) => Promise<void>,   실패하면 reject — 화면은 그때 이동하지 않는다. jobId 는 재진입(앱 재실행) 대비
 *   stop: (jobId?: string) => Promise<void>,
 * }}
 */
export function useLiveSession() {
  // useSyncExternalStore = React 밖의 저장소(liveClient)를 구독하고, 상태가 바뀌면 이 훅을 쓰는 화면을 다시 그린다
  const { status, jobId, startedAt, cameraOnline, cameraBusy, held, source, camera, cameraError, reconnecting, error } = useSyncExternalStore(liveClient.subscribe, liveClient.getState);
  // 함수들은 liveClient 의 것을 그대로 넘긴다. videoRef 는 <VideoSurface ref={videoRef}> 처럼 ref 로 쓴다
  return {
    status,
    jobId,
    startedAt,
    cameraOnline,
    cameraBusy,
    held,
    source,
    camera,
    cameraError,
    reconnecting,
    error,
    videoRef: liveClient.attachVideo,
    prepare: liveClient.prepare,
    releaseCamera: liveClient.releaseCamera,
    start: liveClient.start,
    pause: liveClient.pause,
    resume: liveClient.resume,
    stop: liveClient.stop,
  };
}
