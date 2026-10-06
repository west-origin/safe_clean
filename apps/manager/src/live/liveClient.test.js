// 매니저 liveClient 테스트. http.js 를 가짜 서버(test/fakeHttp.js)로 바꿔 끼우고
// start → pause → resume → stop 의 상태 변화, 서버가 거부·실패했을 때의 상태, 재시도·프리뷰 재연결 타이머를 확인한다.
// liveClient 는 모듈에 하나뿐인 객체라 테스트끼리 상태를 같이 쓴다 — 각 테스트 끝에서 stop()/reset() 으로 비운다.
// 가짜 서버의 프리뷰 주소는 ws:// 라서 roomConnector 는 실제 LiveKit 에 연결하지 않는다.
import { describe, it, expect, vi } from 'vitest';
import { liveClient } from './liveClient.js';

// import 줄보다 아래에 있어도 vitest 가 vi.mock 을 맨 위로 끌어올린다 — 그래서 liveClient → manager.js 가 부르는 http.js 도 가짜 서버다
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

describe('manager liveClient (서버 세션)', () => {
  it('start 는 서버 세션을 열고 상태를 받으며, pause/resume 은 서버 확인 뒤 상태를 바꾸고, stop 은 세션을 닫는다', async () => {
    // __fake = 가짜 서버의 상태와 호출 기록 (calls 에 보낸 요청이 쌓인다). 가짜 start 는 바로 status=live 를 준다
    const { __fake } = await import('../api/http.js');
    const listener = vi.fn();
    const unsubscribe = liveClient.subscribe(listener);
    await liveClient.start('job-1');
    expect(liveClient.getState().status).toBe('live');
    expect(liveClient.getState().jobId).toBe('job-1');
    expect(liveClient.getState().cameraOnline).toBe(true);
    expect(__fake.calls.find((c) => c.path === '/live/sessions/job-1/start')).toBeTruthy();
    await liveClient.pause();
    expect(liveClient.getState().status).toBe('paused');
    await liveClient.resume();
    expect(liveClient.getState().status).toBe('live');
    await liveClient.stop();
    expect(liveClient.getState().status).toBe('ended');
    expect(__fake.calls.find((c) => c.path === '/live/sessions/job-1/stop')).toBeTruthy();
    expect(listener).toHaveBeenCalled();
    unsubscribe();
  });

  it('다음 작업의 start 는 앞 작업의 시작 시각을 잇지 않는다 (검수 4차)', async () => {
    // 가짜 시계: Date.now() 를 정한 시각으로 고정해 두 작업의 시작 시각(startedAt)을 비교한다
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-22T01:00:00Z'));
    await liveClient.start('job-a');
    const first = liveClient.getState().startedAt;
    await liveClient.stop();
    expect(liveClient.getState().startedAt).toBeNull();
    vi.setSystemTime(new Date('2026-09-22T05:00:00Z'));
    await liveClient.start('job-b');
    expect(liveClient.getState().startedAt).toBeGreaterThan(first);
    await liveClient.stop();
    vi.useRealTimers();
  });

  it('서버가 mute 를 확인하지 못하면 pause 가 실패하고 상태는 live 그대로다 (화면이 일시정지 화면으로 가지 않는다)', async () => {
    const { __fake } = await import('../api/http.js');
    await liveClient.start('job-2');
    __fake.livePauseFails = true; // 가짜 서버의 pause 가 502 LIVE_PAUSE_UNCONFIRMED 로 실패한다
    await expect(liveClient.pause()).rejects.toMatchObject({ code: 'LIVE_PAUSE_UNCONFIRMED' });
    expect(liveClient.getState().status).toBe('live');
    __fake.livePauseFails = false;
    await liveClient.stop();
  });

  it('서버가 시작을 거부하면 status=error 로 남기고 던지지 않는다 (작업은 이미 시작됐으므로)', async () => {
    const { __fake } = await import('../api/http.js');
    const original = __fake.live; // 끝나고 되돌릴 가짜 서버의 라이브 상태
    const failing = 'job-no-device';
    // 바디캠이 등록되지 않은 매니저처럼 start 를 거부하게 만든다
    __fake.startFails = true; // 가짜 서버의 start 가 409 INVALID_STATE 로 거부한다 → 409 라 liveClient 는 재시도 타이머를 걸지 않는다
    await liveClient.start(failing);
    expect(liveClient.getState().status).toBe('error'); // 작업은 이미 시작됐으니 화면은 진행한다 (409 라 자동으로 다시 붙지는 않는다)
    expect(liveClient.getState().jobId).toBe(failing);
    __fake.startFails = false;
    __fake.live = original;
    liveClient.reset();
  });

  it('순간 장애로 start 가 실패하면 10초 뒤 다시 붙고, 늦게 온 폴링 응답은 stop 뒤 상태를 덮지 않는다', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const http = await import('../api/http.js');
    const realGet = http.__fake.calls; // 호출 기록
    // 네트워크 실패처럼: 첫 start 만 실패시킨다
    const orig = __fake.startFails;
    let failOnce = true;
    const mod = await import('../api/manager.js');
    // vi.spyOn = 모듈의 함수를 테스트 동안 가짜 구현으로 바꾼다 (mockRestore 로 되돌린다).
    // 첫 호출은 네트워크 오류(status 0) → 재시도 대상, 그 뒤는 바디캠 대기(waiting_camera) 응답
    const spy = vi.spyOn(mod, 'startLiveSession').mockImplementation(async () => {
      if (failOnce) {
        failOnce = false;
        throw Object.assign(new Error('offline'), { status: 0, code: 'NETWORK' });
      }
      return { status: 'waiting_camera', cameraOnline: false, cameraBusy: false, preview: { url: 'ws://localhost:7880', token: 't' } };
    });
    await liveClient.start('job-retry');
    expect(liveClient.getState().status).toBe('error');
    // advanceTimersByTimeAsync = 가짜 시계를 n ms 앞으로 돌리며 그 사이 타이머와 비동기 작업을 실행한다
    await vi.advanceTimersByTimeAsync(10000); // 재시도
    expect(liveClient.getState().status).toBe('connecting');
    expect(spy).toHaveBeenCalledTimes(2);
    spy.mockRestore();
    __fake.startFails = orig;
    await liveClient.stop();
    expect(liveClient.getState().status).toBe('ended');
    await vi.advanceTimersByTimeAsync(10000); // 폴링·재시도 타이머가 살아 있지 않다
    expect(liveClient.getState().status).toBe('ended');
    expect(realGet.length).toBeGreaterThan(0);
    vi.useRealTimers();
    liveClient.reset();
  });
});

describe('manager liveClient — 프리뷰 재연결 (외부 검토 4차)', () => {
  it('서버 세션은 열렸는데 프리뷰 연결이 실패하면 다음 폴링 때 다시 붙는다', async () => {
    // connectRoom 을 가로채 첫 연결만 실패시킨다 → 대기 시간(10초) 동안은 폴링이 다시 붙지 않고, 지난 뒤 한 번 더 붙는지 센다
    vi.useFakeTimers();
    const connector = await import('./roomConnector.js');
    let attempts = 0;
    const spy = vi.spyOn(connector, 'connectRoom').mockImplementation(async () => {
      attempts += 1;
      if (attempts === 1) throw new Error('ws down');
      return { disconnect: vi.fn() };
    });
    await liveClient.start('job-preview');
    expect(attempts).toBe(1);
    expect(liveClient.getState().status).not.toBe('error'); // 세션은 유효 — 오류로 보이지 않는다
    await vi.advanceTimersByTimeAsync(3000); // 3초 폴링 — 아직 10초 전이라 두드리지 않는다
    expect(attempts).toBe(1);
    await vi.advanceTimersByTimeAsync(9000); // 10초가 지난 뒤 폴링 → 프리뷰 재시도
    expect(attempts).toBe(2);
    await vi.advanceTimersByTimeAsync(30000); // 붙은 뒤에는 더 시도하지 않는다
    expect(attempts).toBe(2);
    spy.mockRestore();
    vi.useRealTimers();
    liveClient.reset();
  });
});
