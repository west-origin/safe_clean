// 매니저 liveClient.capturePhoto 테스트 (청소 전·후 사진). 미리보기 <video> 의 장면을 가운데 3:4 로 잘라 JPEG 파일로 만드는지,
// 영상이 아직 없으면 notReady 로 거절하는지 본다. jsdom 의 canvas 는 그리지 못해 createElement('canvas') 를 가짜로 바꾼다.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { liveClient } from './liveClient.js';

vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

/** videoWidth·videoHeight 만 가진 가짜 <video> */
const fakeVideo = (videoWidth, videoHeight) => {
  const video = document.createElement('video');
  // jsdom 의 videoWidth·videoHeight 는 읽기 전용(늘 0)이라 이 요소에만 값을 덮어 둔다
  Object.defineProperty(video, 'videoWidth', { value: videoWidth });
  Object.defineProperty(video, 'videoHeight', { value: videoHeight });
  return video;
};

/** 그린 인자를 기록하고 toBlob 으로 JPEG Blob 을 돌려주는 가짜 canvas */
function stubCanvas() {
  const drawImage = vi.fn();
  const canvas = { width: 0, height: 0, getContext: () => ({ drawImage }), toBlob: (cb, type) => cb(new Blob(['x'], { type })) };
  const original = document.createElement.bind(document);
  vi.spyOn(document, 'createElement').mockImplementation((tag) => (tag === 'canvas' ? canvas : original(tag)));
  return { canvas, drawImage };
}

afterEach(() => {
  vi.restoreAllMocks();
  liveClient.attachVideo(null);
});

describe('manager liveClient.capturePhoto', () => {
  it('영상이 아직 없으면 photo: notReady 로 거절한다', async () => {
    await expect(liveClient.capturePhoto()).rejects.toThrow('photo: notReady');
    const video = document.createElement('video'); // videoWidth 0 = 아직 장면이 없다
    liveClient.attachVideo(video);
    await expect(liveClient.capturePhoto()).rejects.toThrow('photo: notReady');
  });

  it('가로 1280×720 영상은 가운데 540×720 을 잘라 3:4 JPEG 로 만든다', async () => {
    const { canvas, drawImage } = stubCanvas();
    const video = fakeVideo(1280, 720);
    liveClient.attachVideo(video);
    const file = await liveClient.capturePhoto();
    expect(file.type).toBe('image/jpeg');
    expect([canvas.width, canvas.height]).toEqual([540, 720]);
    expect(drawImage).toHaveBeenCalledWith(video, 370, 0, 540, 720, 0, 0, 540, 720);
  });

  it('세로로 긴 영상은 위아래를 잘라 내고, 긴 변 1600 을 넘으면 줄인다', async () => {
    const { canvas, drawImage } = stubCanvas();
    const video = fakeVideo(1800, 4000);
    liveClient.attachVideo(video);
    await liveClient.capturePhoto();
    // 3:4 영역 = 1800×2400 (위아래 800 씩 잘림) → 긴 변 2400 을 1600 으로 줄인다
    expect([canvas.width, canvas.height]).toEqual([1200, 1600]);
    expect(drawImage).toHaveBeenCalledWith(video, 0, 800, 1800, 2400, 0, 0, 1200, 1600);
  });
});
