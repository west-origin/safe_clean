// 라이브 시청 화면(세로 ServiceLivePortrait · 가로 ServiceLiveLandscape) 테스트.
// 확인하는 것: ?booking 없이 열면 내 예약으로 보낸다, 실시간(mode=live)에는 안심 배너가 있고 15초 탐색 버튼이 없다,
// 녹화본(?replay=1)에는 재생/멈춤·15초 탐색이 있고 안심 배너가 없다 (세로·가로 모두), 세로 녹화본 컨트롤은 밝은 배경 tone 이다.
// 서버 대신 test/fakeHttp.js 의 가짜 서버를 쓴다 —
// r0(LIVE_BOOKING_ID)은 라이브 진행 중, r3(READY_BOOKING_ID)은 녹화본이 준비된 예약.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import ServiceLivePortrait from './ServiceLivePortrait.jsx';
import ServiceLiveLandscape from './ServiceLiveLandscape.jsx';
import { liveClient } from '../../live/liveClient.js';
import { LIVE_BOOKING_ID, READY_BOOKING_ID } from '../../test/fakeHttp.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 주어진 주소로 두 라이브 화면을 띄운다. /reservations 는 '돌려보냈는지' 를 글자로 확인하려고 둔 자리 화면이다
function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/service/live" element={<ServiceLivePortrait />} />
        <Route path="/service/live/landscape" element={<ServiceLiveLandscape />} />
        <Route path="/reservations" element={<p>내 예약 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Service/Live 화면 — 모드별 컨트롤', () => {
  // liveClient 는 앱에 하나뿐인 객체라 상태가 테스트 사이에 남는다 — 매 테스트 뒤 세션에서 바로 나가 초기화한다
  afterEach(() => liveClient.leave());

  it('예약 id 없이 열리면 내 예약으로 보낸다', async () => {
    renderAt('/service/live');
    expect(await screen.findByText('내 예약 화면')).toBeInTheDocument();
  });

  it('실시간: 안심 배너가 있고 재생·탐색 컨트롤은 없다', async () => {
    renderAt(`/service/live?booking=${LIVE_BOOKING_ID}`);
    expect(await screen.findByText('안심하고 지켜보세요!')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '15초 뒤로' })).toBeNull();
  });

  // 세로 화면을 먼저 확인하고, 내린 뒤 같은 예약을 가로 화면으로 다시 띄워 15초 앞으로 버튼을 확인한다.
  // unmount 뒤 leave: 화면이 사라지면 훅이 release(잠깐 뒤 나가기)만 걸어 두므로, 기다리지 않고 바로 앞 세션을 정리한다
  it('녹화본: 재생/멈춤·15초 탐색이 있고 안심 배너는 없다 (세로·가로 모두)', async () => {
    const { unmount } = renderAt(`/service/live?booking=${READY_BOOKING_ID}&replay=1`);
    await waitFor(() => expect(screen.getByRole('button', { name: '15초 뒤로' })).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /재생|일시정지/ })).toBeInTheDocument();
    expect(screen.queryByText('안심하고 지켜보세요!')).toBeNull();
    // 세로 녹화본 컨트롤은 영상 아래 흰 화면 배경에 놓이므로 밝은 배경 tone(onLight) 으로 그린다
    expect(screen.getByRole('slider', { name: '재생 위치' }).parentElement.className).toContain('onLight');
    expect(screen.getByRole('button', { name: '15초 뒤로' }).className).toContain('onLight');
    unmount();
    liveClient.leave();
    renderAt(`/service/live/landscape?booking=${READY_BOOKING_ID}&replay=1`);
    await waitFor(() => expect(screen.getByRole('button', { name: '15초 앞으로' })).toBeInTheDocument());
    // 가로 화면은 컨트롤이 영상 위에 떠 있어 기본 tone(onVideo) 그대로다
    expect(screen.getByRole('button', { name: '15초 앞으로' }).className).toContain('onVideo');
  });
});
