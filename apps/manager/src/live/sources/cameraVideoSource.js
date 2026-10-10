// 영상 입력: 휴대폰 후면 카메라 (BACKEND_PLAN.md §8.0, 2026-10-10 "바디캠이 아닌 스마트폰으로").
// 이 파일은 카메라에서 영상 트랙 하나를 받아 오는 일만 한다 — LiveKit·서버를 모른다. 송출은 liveClient.js·roomConnector.js 가 한다.
// 영상만 받는다(마이크 없음, 결정 §13-23). 마이크 권한은 Manifest·Info.plist 에 선언만 되어 있다.
// 나중에 다른 입력(바디캠 SDK·USB 카메라 등)이 필요하면 같은 모양(start/stop/getTrack/onEnded)의 파일을 sources/ 에 더하고 index.js 에서 고른다.

/**
 * 영상 입력 하나. 모든 입력 파일이 이 모양을 지킨다 (sources/index.js 의 VideoSource).
 * @typedef {object} VideoSource
 * @property {string} kind 입력 이름 (예: 'camera')
 * @property {() => Promise<MediaStreamTrack>} start 영상 트랙을 켠다. 이미 켜져 있으면 그 트랙을 돌려준다. 실패하면 CameraError 를 던진다
 * @property {() => void} stop 트랙을 끄고 카메라를 놓는다
 * @property {() => MediaStreamTrack|null} getTrack 지금 켜진 트랙 (없으면 null)
 * @property {(fn: () => void) => () => void} onEnded 트랙이 저절로 끝났을 때(다른 앱이 카메라를 가져감·백그라운드 등) 알림. 반환값은 해제 함수
 */

/** 카메라를 켜지 못한 이유. code: denied = 권한 거부 · notFound = 카메라 없음 · busy = 다른 앱이 사용 중 · failed = 그 밖 */
export class CameraError extends Error {
  constructor(code, cause) {
    super(`camera: ${code}`);
    this.name = 'CameraError';
    this.code = code;
    this.cause = cause;
  }
}

// 기본 화질: 720p · 24fps. 휴대폰 데이터·발열을 생각해 1080p 를 쓰지 않는다 (LiveKit 이 망 상태에 맞춰 더 낮춘다)
const VIDEO_CONSTRAINTS = {
  facingMode: { ideal: 'environment' }, // 후면 카메라 (없으면 있는 카메라)
  width: { ideal: 1280 },
  height: { ideal: 720 },
  frameRate: { ideal: 24, max: 30 },
};

/** getUserMedia 오류 이름 → CameraError 코드 */
function toCameraError(e) {
  const name = e?.name;
  if (name === 'NotAllowedError' || name === 'SecurityError') return new CameraError('denied', e);
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return new CameraError('notFound', e);
  if (name === 'NotReadableError' || name === 'AbortError') return new CameraError('busy', e);
  return new CameraError('failed', e);
}

/**
 * 휴대폰 카메라 입력을 만든다. 한 번 만든 것을 여러 번 start/stop 해도 된다
 * @returns {VideoSource}
 */
export function createCameraVideoSource() {
  let track = null; // 지금 켜진 영상 트랙
  const endedListeners = new Set();
  // 트랙이 저절로 끝나면(ended) 비우고 알린다. stop() 으로 끈 것은 알리지 않는다
  const handleEnded = () => {
    track = null;
    endedListeners.forEach((fn) => fn());
  };

  return {
    kind: 'camera',
    async start() {
      if (track && track.readyState === 'live') return track;
      if (!globalThis.navigator?.mediaDevices?.getUserMedia) throw new CameraError('notFound');
      let stream;
      try {
        // 처음 부르면 OS 가 카메라 권한을 묻는다 (Android WebView 는 Capacitor 가 앱 권한 요청으로 넘긴다)
        stream = await navigator.mediaDevices.getUserMedia({ video: VIDEO_CONSTRAINTS, audio: false });
      } catch (e) {
        throw toCameraError(e);
      }
      track = stream.getVideoTracks()[0] ?? null;
      if (!track) throw new CameraError('notFound');
      track.contentHint = 'motion'; // 움직임이 많은 영상 — 인코더가 선명도보다 부드러움을 우선한다
      track.addEventListener('ended', handleEnded);
      return track;
    },
    stop() {
      if (!track) return;
      track.removeEventListener('ended', handleEnded);
      track.stop();
      track = null;
    },
    getTrack: () => track,
    onEnded(fn) {
      endedListeners.add(fn);
      return () => endedListeners.delete(fn);
    },
  };
}
