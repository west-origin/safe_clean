// 매니저 liveClient — 휴대폰 송출 모드(서버 source='app', BACKEND_PLAN.md §8.0) 테스트.
// http.js 는 가짜 서버(test/fakeHttp.js, __fake.liveSource='app'), roomConnector.js 는 목(실제 LiveKit 없이 publishRoom 호출만 기록),
// 카메라(navigator.mediaDevices.getUserMedia)는 가짜 트랙을 주는 함수로 바꾼다.
// liveClient 는 모듈에 하나뿐인 객체라 테스트끼리 상태를 같이 쓴다 — 각 테스트 끝에서 reset() 으로 비운다.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { liveClient } from './liveClient.js';
import * as connector from './roomConnector.js';

vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());
// publishRoom 은 가짜 송출 연결을 돌려준다 (disconnect·replaceTrack·unmute 호출을 기록)
const publisher = { disconnect: vi.fn(async () => {}), replaceTrack: vi.fn(async () => {}), unmute: vi.fn(async () => {}) };
vi.mock('./roomConnector.js', () => ({ connectRoom: vi.fn(async () => null), publishRoom: vi.fn(async () => publisher) }));

/** getUserMedia 가 돌려줄 가짜 영상 트랙 */
function fakeTrack() {
  return { readyState: 'live', stop: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() };
}

describe('manager liveClient (휴대폰 송출)', () => {
  let track;
  let getUserMedia;
  beforeEach(async () => {
    const { __fake } = await import('../api/http.js');
    __fake.liveSource = 'app';
    track = fakeTrack();
    getUserMedia = vi.fn(async () => ({ getVideoTracks: () => [track] }));
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });
    vi.mocked(connector.publishRoom).mockClear();
    Object.values(publisher).forEach((fn) => fn.mockClear());
  });
  afterEach(async () => {
    liveClient.reset();
    const { __fake } = await import('../api/http.js');
    __fake.liveSource = undefined;
    vi.unstubAllGlobals();
  });

  it('prepare 는 서버의 source 가 app 이면 후면 카메라를 영상만으로 켜고, 다시 불러도 카메라를 두 번 열지 않는다', async () => {
    await liveClient.prepare('job-p');
    expect(liveClient.getState().source).toBe('app');
    expect(liveClient.getState().camera).toBe('ready');
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    expect(getUserMedia.mock.calls[0][0]).toMatchObject({ audio: false, video: { facingMode: { ideal: 'environment' } } });
    await liveClient.prepare('job-p');
    expect(getUserMedia).toHaveBeenCalledTimes(1);
  });

  it('전/후 사진 카메라는 세션 API나 송출 없이 켜지고 촬영 화면을 떠나면 꺼진다', async () => {
    const { __fake } = await import('../api/http.js');
    __fake.calls.length = 0;
    await liveClient.preparePhoto();
    expect(liveClient.getState().camera).toBe('ready');
    expect(liveClient.getState().status).toBe('idle');
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    expect(__fake.calls).toEqual([]);
    expect(connector.publishRoom).not.toHaveBeenCalled();
    liveClient.releasePhoto();
    expect(track.stop).toHaveBeenCalled();
    expect(liveClient.getState().camera).toBe('off');
  });

  it('카메라 권한을 거부하면 camera=error · cameraError=denied 로 남기고 던지지 않는다', async () => {
    getUserMedia.mockRejectedValueOnce(Object.assign(new Error('no'), { name: 'NotAllowedError' }));
    await liveClient.prepare('job-d');
    expect(liveClient.getState().camera).toBe('error');
    expect(liveClient.getState().cameraError).toBe('denied');
  });

  it('바디캠 모드(source=ingress)면 prepare 는 카메라를 켜지 않는다', async () => {
    const { __fake } = await import('../api/http.js');
    __fake.liveSource = undefined;
    await liveClient.prepare('job-i');
    expect(liveClient.getState().source).toBe('ingress');
    expect(getUserMedia).not.toHaveBeenCalled();
  });

  it('start 는 송출 토큰으로 같은 카메라 트랙을 publish 하고, stop 은 송출을 내리고 카메라를 끈다', async () => {
    await liveClient.prepare('job-s');
    await liveClient.start('job-s');
    expect(connector.publishRoom).toHaveBeenCalledTimes(1);
    expect(vi.mocked(connector.publishRoom).mock.calls[0][0]).toMatchObject({ token: 'publish-token', mediaTrack: track });
    expect(connector.connectRoom).not.toHaveBeenCalled(); // 바디캠 프리뷰 시청은 하지 않는다
    expect(getUserMedia).toHaveBeenCalledTimes(1); // 미리보기 트랙을 그대로 송출한다
    await liveClient.stop();
    expect(publisher.disconnect).toHaveBeenCalled();
    expect(track.stop).toHaveBeenCalled();
    expect(liveClient.getState().camera).toBe('off');
  });

  it('청소를 시작하지 않고 준비 화면을 떠나면 카메라를 끄고, 송출 중이면 끄지 않는다', async () => {
    await liveClient.prepare('job-r');
    liveClient.releaseCamera();
    expect(track.stop).toHaveBeenCalledTimes(1);
    await liveClient.prepare('job-r');
    await liveClient.start('job-r');
    liveClient.releaseCamera();
    expect(liveClient.getState().camera).toBe('ready');
  });

  it('재개 확인이 실패하면(원격 unmute 불가) 휴대폰이 스스로 mute 를 풀고 한 번 더 확인받는다', async () => {
    const { __fake } = await import('../api/http.js');
    await liveClient.start('job-u');
    await liveClient.pause();
    __fake.liveResumeFailsOnce = true;
    await liveClient.resume();
    expect(publisher.unmute).toHaveBeenCalledTimes(1);
    expect(liveClient.getState().status).toBe('live');
  });
});
