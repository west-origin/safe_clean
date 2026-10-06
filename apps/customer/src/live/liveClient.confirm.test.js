// liveClient 의 '트랙 이벤트 직후 확인' 테스트: 음소거·영상 도착 콜백 뒤 refresh(confirm) 가
// 서버가 따라올 때까지 1초 간격으로 다시 묻고, 끝내 다르면 서버를 따르는지 본다. 서버는 가짜(test/fakeHttp.js).
import { describe, it, expect, vi, afterEach } from 'vitest';
import { LIVE_BOOKING_ID } from '../test/fakeHttp.js';

// api/http.js 를 가짜 서버로 바꾼다. __fake.liveStates[id] 로 서버가 돌려줄 live.state 를 정한다
vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

// 방 연결은 바로 붙고, 트랙·음소거 콜백은 테스트가 직접 부른다
const rooms = [];
vi.mock('./roomConnector.js', () => ({
  connectRoom: async (opts) => {
    rooms.push(opts);
    return { disconnect: vi.fn() };
  },
}));

const flush = () => vi.advanceTimersByTimeAsync(0); // 가짜 시계에서 밀린 Promise·0ms 타이머만 처리한다 (시간은 그대로)

/** 자체 점검 #34·#38 — 트랙 이벤트 직후, 서버가 아직 커밋하기 전의 응답이 상태를 거꾸로 덮지 않는다 */
describe('customer liveClient 트랙 이벤트 확인', () => {
  // 서버 상태·세션·가짜 시계·방 기록을 비워 다음 테스트에 남기지 않는다
  afterEach(async () => {
    const { __fake } = await import('../api/http.js');
    __fake.liveStates = {};
    const { liveClient } = await import('./liveClient.js');
    liveClient.leave();
    vi.useRealTimers();
    rooms.length = 0;
  });

  it('음소거 직후 서버가 아직 live 를 주면 paused 를 유지하고, 서버가 따라오면 그 세션을 반영한다 (#34)', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const { liveClient } = await import('./liveClient.js');
    await liveClient.join(LIVE_BOOKING_ID);
    expect(liveClient.getState().status).toBe('live');

    rooms[0].onMuted(true); // 서버는 아직 커밋 전(live)
    await flush();
    expect(liveClient.getState().status).toBe('paused');

    __fake.liveStates[LIVE_BOOKING_ID] = 'paused';
    await vi.advanceTimersByTimeAsync(1000);
    expect(liveClient.getState().status).toBe('paused');
    expect(liveClient.getState().session.live.state).toBe('paused');
  });

  it('몇 번 물어도 서버가 다르면 서버를 따른다', async () => {
    vi.useFakeTimers();
    const { liveClient } = await import('./liveClient.js');
    await liveClient.join(LIVE_BOOKING_ID);
    rooms[0].onMuted(true);
    await flush();
    expect(liveClient.getState().status).toBe('paused');
    // 서버는 계속 live. 1초 간격으로 다시 묻다가 횟수(TRACK_CONFIRM_TRIES)가 다하면 서버 값을 따른다
    await vi.advanceTimersByTimeAsync(5000);
    expect(liveClient.getState().status).toBe('live');
  });

  it('카메라 대기 중 영상 트랙이 오면 30초 폴링을 기다리지 않고 live 로 바뀐다 (#38)', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const { liveClient } = await import('./liveClient.js');
    __fake.liveStates[LIVE_BOOKING_ID] = 'waiting';
    await liveClient.join(LIVE_BOOKING_ID);
    expect(liveClient.getState().status).toBe('connecting');

    rooms[0].onVideoTrack({ attach: vi.fn(), detach: vi.fn() }); // 서버는 아직 웹훅 처리 전(waiting)
    await flush();
    expect(liveClient.getState().status).toBe('connecting');
    __fake.liveStates[LIVE_BOOKING_ID] = 'live';
    await vi.advanceTimersByTimeAsync(1000);
    expect(liveClient.getState().status).toBe('live');
  });

  it('일시정지 전에 나간 폴링의 응답이 늦게 와도 더 새 paused 를 live 로 덮지 않는다', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const { liveClient } = await import('./liveClient.js');
    await liveClient.join(LIVE_BOOKING_ID);
    expect(liveClient.getState().status).toBe('live');

    // 30초 폴링이 나갔다 — 서버는 아직 live 로 답했지만 그 응답이 늦게 도착한다
    let arrive;
    __fake.holdOnce.set(`GET /customer/bookings/${LIVE_BOOKING_ID}/live`, new Promise((r) => (arrive = r)));
    const stale = liveClient.refresh(LIVE_BOOKING_ID);
    await flush();

    __fake.liveStates[LIVE_BOOKING_ID] = 'paused'; // 매니저가 일시정지(서버 반영)
    rooms[0].onMuted(true); // 트랙 이벤트 → paused, 확인 요청은 paused 를 받는다
    await flush();
    expect(liveClient.getState().status).toBe('paused');

    arrive(); // 옛 live 응답 도착
    await stale;
    await flush();
    expect(liveClient.getState().status).toBe('paused');
    expect(liveClient.getState().session.live.state).toBe('paused');
  });

  it('트랙 이벤트의 확인이 끝나기 전에 나간 보통 폴링도 서버의 옛 live 로 paused 를 덮지 않는다', async () => {
    vi.useFakeTimers();
    const { __fake } = await import('../api/http.js');
    const { liveClient } = await import('./liveClient.js');
    await liveClient.join(LIVE_BOOKING_ID);
    rooms[0].onMuted(true); // 서버는 아직 커밋 전(live) — 확인 요청이 1초 뒤 다시 묻기로 한다
    await flush();
    expect(liveClient.getState().status).toBe('paused');

    await liveClient.refresh(LIVE_BOOKING_ID); // 이벤트 뒤·커밋 전에 나간 30초 폴링 (또는 앱 복귀 갱신)
    await flush();
    expect(liveClient.getState().status).toBe('paused');

    __fake.liveStates[LIVE_BOOKING_ID] = 'paused'; // 서버 커밋
    await vi.advanceTimersByTimeAsync(1000); // 확인이 서버 값을 받아 끝난다
    __fake.liveStates[LIVE_BOOKING_ID] = 'live'; // 매니저가 재개(트랙 이벤트는 테스트에서 생략)
    await liveClient.refresh(LIVE_BOOKING_ID); // 확인이 끝난 뒤의 보통 폴링은 다시 서버를 따른다
    await flush();
    expect(liveClient.getState().status).toBe('live');
  });
});

