// liveClient(고객 라이브 시청·녹화본) 테스트: 가짜 서버(test/fakeHttp.js)로 join·녹화본 재생·폴링·leave·release 흐름을 본다.
// roomConnector 는 목으로 바꾸지 않는다 — 가짜 서버가 주는 시청 토큰 주소가 ws:// 라 connectRoom 이 방 없이 null 을 돌려준다.
// liveClient 는 앱에 하나뿐인 객체라 테스트끼리 상태를 나눠 쓴다 — 그래서 테스트마다·afterEach 에서 leave 한다.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { liveClient } from './liveClient.js';
import { LIVE_BOOKING_ID, READY_BOOKING_ID, UPLOADING_BOOKING_ID } from '../test/fakeHttp.js';

// api/http.js 를 가짜 서버로 바꾼다 (vi.mock 은 import 보다 먼저 적용된다). __fake 로 호출 기록을 보고 서버 상태를 바꾼다
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

describe('customer liveClient (시청·녹화본)', () => {
  it('예약 없이 join 하면 mock 세션과 대기 상태, 음소거 토글이 <video>.muted 에 반영되며, leave 로 끝난다', async () => {
    await liveClient.join();
    expect(liveClient.getState().session.manager.name).toBe('김미영 매니저');
    expect(liveClient.getState().status).toBe('idle');
    const video = document.createElement('video');
    liveClient.attachVideo(video);
    expect(video.muted).toBe(true);
    liveClient.toggleMute();
    expect(video.muted).toBe(false);
    liveClient.leave();
    expect(liveClient.getState().status).toBe('ended');
    liveClient.toggleMute(); // leave 는 muted 를 되돌리지 않는다 — 처음 값(true)으로 돌려 다음 테스트에 남기지 않는다
  });

  it('진행 중 예약은 시청 토큰을 받고 live 상태가 되며, 서버 문구를 세션에 담는다', async () => {
    const { __fake } = await import('../api/http.js');
    await liveClient.join(LIVE_BOOKING_ID);
    expect(liveClient.getState().status).toBe('live');
    expect(liveClient.getState().mode).toBe('live');
    expect(liveClient.getState().session.live.available).toBe(true);
    expect(__fake.calls.find((c) => c.method === 'POST' && c.path === `/live/sessions/${LIVE_BOOKING_ID}/token`)).toBeTruthy();
    liveClient.leave();
  });

  it('아직 시작 전인 예약은 토큰 없이(409) 카메라 대기 상태만 보여준다', async () => {
    // 가짜 서버에서 r2 는 live.state 가 waiting 이고, 시청 토큰 요청은 LIVE_NOT_AVAILABLE(409) 로 거절된다
    await liveClient.join('r2');
    expect(liveClient.getState().status).toBe('connecting');
    expect(liveClient.getState().error).toBeNull();
    liveClient.leave();
  });

  it('녹화본은 서명 URL 을 <video src> 로 붙이고 탐색·재생/멈춤이 동작한다', async () => {
    const video = document.createElement('video');
    // jsdom(테스트용 가짜 브라우저)의 <video> 는 실제로 재생하지 못해 play/pause 를 가짜 함수로 바꾼다
    video.play = vi.fn().mockResolvedValue();
    video.pause = vi.fn();
    liveClient.attachVideo(video);
    await liveClient.join(READY_BOOKING_ID, { replay: true });
    expect(liveClient.getState().mode).toBe('replay');
    expect(liveClient.getState().status).toBe('paused');
    expect(video.src).toContain('recordings/r3.mp4');
    liveClient.togglePause();
    expect(video.play).toHaveBeenCalled();
    expect(liveClient.getState().status).toBe('live');
    liveClient.togglePause();
    expect(video.pause).toHaveBeenCalled();
    liveClient.forward15();
    expect(video.currentTime).toBeGreaterThanOrEqual(0);
    liveClient.leave();
  });

  it('실시간을 보다가 replay 로 다시 join 하면 녹화본 모드로 바뀐다 (같은 예약이어도 조기 반환하지 않는다)', async () => {
    await liveClient.join(LIVE_BOOKING_ID);
    expect(liveClient.getState().mode).toBe('live');
    await liveClient.join(LIVE_BOOKING_ID, { replay: true });
    expect(liveClient.getState().mode).toBe('replay'); // 녹화본이 아직 없으면 ended 안내
    liveClient.leave();
  });

  it('connectLive 가 겹쳐 불려도 시청 토큰은 한 번만 받는다', async () => {
    const { __fake } = await import('../api/http.js');
    await liveClient.join(LIVE_BOOKING_ID);
    const count = () => __fake.calls.filter((c) => c.method === 'POST' && c.path === `/live/sessions/${LIVE_BOOKING_ID}/token`).length;
    const before = count();
    await Promise.all([liveClient.connectLive(LIVE_BOOKING_ID), liveClient.connectLive(LIVE_BOOKING_ID), liveClient.connectLive(LIVE_BOOKING_ID)]);
    expect(count() - before).toBeLessThanOrEqual(1); // 진행 중이면 같은 시도를 기다린다 (테스트 환경은 wss 가 아니라 방이 없어 한 번은 다시 받는다)
    liveClient.leave();
  });

  it('녹화본 <video> 가 복구할 수 없는 오류(DECODE)를 내면 재시도 없이 오류 상태로 끝난다', async () => {
    const video = document.createElement('video');
    liveClient.attachVideo(video);
    await liveClient.join(READY_BOOKING_ID, { replay: true });
    // <video>.error 를 code 3(MEDIA_ERR_DECODE) 로 꾸미고 error 이벤트를 보내 onReplayError 를 부른다
    Object.defineProperty(video, 'error', { value: { code: 3 }, configurable: true });
    video.dispatchEvent(new Event('error'));
    await Promise.resolve();
    expect(liveClient.getState().status).toBe('error');
    expect(liveClient.getState().error?.message).toContain('재생할 수 없어요');
    liveClient.leave();
  });

  // afterEach 는 적힌 위치와 관계없이 이 describe 안의 모든 테스트 뒤에 돈다: 가짜 시계를 풀고 세션을 정리한다
  afterEach(() => {
    vi.useRealTimers();
    liveClient.leave();
  });

  it('끝났는데 녹화본이 업로드 중이면 계속 물어보다가 준비되면 재생 모드로 넘어간다', async () => {
    // 가짜 시계: setInterval 을 실제로 기다리지 않고 advanceTimersByTimeAsync 로 30초를 당겨 폴링 한 번을 돌린다
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    await liveClient.join(UPLOADING_BOOKING_ID);
    expect(liveClient.getState().status).toBe('ended');
    expect(liveClient.getState().mode).toBe('replay');

    __fake.readyRecordings.add(UPLOADING_BOOKING_ID); // 서버에서 업로드가 끝났다
    await vi.advanceTimersByTimeAsync(30000);
    expect(liveClient.getState().status).toBe('paused'); // 녹화본 준비 완료 = 재생 대기
    expect(__fake.calls.some((c) => c.path === `/live/sessions/${UPLOADING_BOOKING_ID}/recording`)).toBe(true);
    __fake.readyRecordings.delete(UPLOADING_BOOKING_ID);
  });

  it('실시간에서 녹화본으로 넘어갈 때 실시간 트랙을 떼고 <video> 소스를 바꾼다', async () => {
    const { __fake } = await import('../api/http.js');
    const video = document.createElement('video');
    video.srcObject = 'live-stream'; // 실시간 트랙이 붙어 있던 것처럼 꾸민 값
    liveClient.attachVideo(video);
    await liveClient.join(READY_BOOKING_ID, { replay: true });
    expect(video.srcObject).toBeNull();
    expect(video.src).toContain('recordings/r3.mp4');
    __fake.calls.length = 0;
  });

  it('leave 뒤에 늦게 도착한 join 응답은 연결·폴링을 되살리지 않는다', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const joining = liveClient.join(LIVE_BOOKING_ID); // 아직 응답 전
    liveClient.leave();
    await joining;
    expect(liveClient.getState().status).toBe('ended');
    expect(liveClient.getState().bookingId).toBeUndefined();
    __fake.calls.length = 0;
    await vi.advanceTimersByTimeAsync(60000); // 폴링 두 번 분량
    expect(__fake.calls.filter((c) => c.path.endsWith('/live')).length).toBe(0); // 폴링 없음
  });

  it('release 는 잠깐 뒤 나가지만 그 사이 같은 세션으로 다시 join 하면(세로↔가로) 유지된다', async () => {
    vi.useFakeTimers();
    await liveClient.join(LIVE_BOOKING_ID);
    liveClient.release();
    await liveClient.join(LIVE_BOOKING_ID);
    await vi.advanceTimersByTimeAsync(1000);
    expect(liveClient.getState().status).toBe('live');
    liveClient.release();
    await vi.advanceTimersByTimeAsync(2500); // 유예 2초
    expect(liveClient.getState().status).toBe('ended');
  });
});
