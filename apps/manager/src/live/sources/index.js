// 영상 입력을 고르는 한 곳 (BACKEND_PLAN.md §8.0). liveClient.js 는 여기서 받은 입력의 start/stop/getTrack/onEnded 만 쓴다.
// 지금은 휴대폰 후면 카메라 하나뿐이다. 바디캠 SDK·USB 카메라 같은 입력이 정해지면 같은 모양의 파일을 이 폴더에 만들고 아래에서 고른다 —
// LiveKit 송출(roomConnector.js)·고객 앱은 바꾸지 않아도 된다.
import { createCameraVideoSource } from './cameraVideoSource.js';

export { CameraError } from './cameraVideoSource.js';

/** @typedef {import('./cameraVideoSource.js').VideoSource} VideoSource */

/** 송출에 쓸 영상 입력을 만든다 @returns {VideoSource} */
export function createVideoSource() {
  return createCameraVideoSource();
}
