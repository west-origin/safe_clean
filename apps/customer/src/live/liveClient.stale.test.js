// liveClient 테스트: 방 연결(connectRoom)이 끝나기 전에 leave 하고 다시 join 했을 때,
// 앞 연결의 늦은 트랙·음소거 콜백이 새 세션의 <video>·상태를 바꾸지 않는지 본다 (joinSeq 비교).
import { describe, it, expect, vi } from 'vitest';
import { LIVE_BOOKING_ID } from '../test/fakeHttp.js';

vi.mock('../api/http.js', async () => (await import('../test/fakeHttp.js')).fakeHttpModule());

// 방 연결을 붙잡아 두고 그 콜백을 나중에 부른다 — 연결이 끝나기 전에 다른 예약으로 옮겨 간 상황
const attempts = [];
vi.mock('./roomConnector.js', () => ({
  connectRoom: (opts) =>
    new Promise((resolve) => {
      attempts.push({ ...opts, resolve });
    }),
}));

/** 재감사 2차 #2 — 옛 예약의 늦은 영상·음소거 이벤트가 새 예약 화면에 닿지 않는다 */
describe('customer liveClient 연결 중 예약 전환', () => {
  it('앞 예약의 늦은 트랙·음소거는 무시하고, 뒤 예약은 자기 연결을 새로 시작한다', async () => {
    const { liveClient } = await import('./liveClient.js');
    const video = document.createElement('video');
    liveClient.attachVideo(video);

    const first = liveClient.join(LIVE_BOOKING_ID);
    // vi.waitFor: 안의 expect 가 통과할 때까지 잠깐씩 다시 확인한다 (토큰을 받은 뒤 connectRoom 이 불릴 때까지)
    await vi.waitFor(() => expect(attempts).toHaveLength(1));
    liveClient.leave(); // 연결이 끝나기 전에 나간다 (first 는 연결이 끝날 때까지 기다린다)

    const second = liveClient.join(LIVE_BOOKING_ID); // 다른 예약(여기서는 새 join)
    await vi.waitFor(() => expect(attempts).toHaveLength(2)); // 앞 시도를 기다리지 않고 새로 붙는다
    const statusBefore = liveClient.getState().status;

    const staleTrack = { attach: vi.fn(), detach: vi.fn() };
    attempts[0].onVideoTrack(staleTrack);
    attempts[0].onMuted(true);
    expect(staleTrack.attach).not.toHaveBeenCalled();
    expect(liveClient.getState().status).toBe(statusBefore);

    const freshTrack = { attach: vi.fn(), detach: vi.fn() };
    attempts[1].onVideoTrack(freshTrack);
    expect(freshTrack.attach).toHaveBeenCalledWith(video);

    // 붙잡아 둔 두 연결을 끝내 join 이 마무리되게 한다 (앞 연결은 seq 가 달라 바로 disconnect 된다)
    attempts[0].resolve({ disconnect: vi.fn() });
    attempts[1].resolve({ disconnect: vi.fn() });
    await Promise.all([first, second]);
    expect(attempts[0].resolve).toBeTypeOf('function');
    liveClient.leave();
  });
});
