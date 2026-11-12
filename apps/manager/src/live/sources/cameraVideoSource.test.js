import { afterEach, expect, it, vi } from 'vitest';
import { createCameraVideoSource } from './cameraVideoSource.js';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

it('첫 프레임을 기다리며 멈춘 카메라는 시간 초과 후 다시 연결할 수 있고, 늦게 열린 트랙은 닫는다', async () => {
  vi.useFakeTimers();
  let finishFirst;
  const lateTrack = { stop: vi.fn() };
  const nextTrack = { readyState: 'live', addEventListener: vi.fn() };
  const getUserMedia = vi.fn()
    .mockImplementationOnce(() => new Promise((resolve) => { finishFirst = resolve; }))
    .mockResolvedValueOnce({ getVideoTracks: () => [nextTrack] });
  vi.stubGlobal('navigator', { mediaDevices: { getUserMedia } });
  const source = createCameraVideoSource();
  const failure = expect(source.start()).rejects.toMatchObject({ code: 'failed' });
  await vi.advanceTimersByTimeAsync(20_000);
  await failure;
  await expect(source.start()).resolves.toBe(nextTrack);
  finishFirst({ getTracks: () => [lateTrack] });
  await Promise.resolve();
  expect(lateTrack.stop).toHaveBeenCalledOnce();
  expect(source.getTrack()).toBe(nextTrack);
});
