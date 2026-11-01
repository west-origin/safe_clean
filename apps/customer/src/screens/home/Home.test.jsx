// 고객 앱 홈 화면(Home.jsx) 테스트.
// 예약 상태·기한 안내 유지, 청소 진입점 분리, 문의 탭 경로를 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Home from './Home.jsx';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다. 가짜 서버는 이 파일에서 한 벌만 만들어지고,
// 테스트가 그 상태 객체(__fake)를 직접 고쳐 서버 응답을 바꾼다 — 이 파일은 고친 것을 되돌리지 않는다.
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// /home 경로 하나만 있는 라우터 안에 홈 화면을 띄운다
function renderHome() {
  return render(
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route path="/home" element={<Home />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 홈 '다가오는 방문' 카드의 안내 — 기한이 지난 방문에 "내 예약에서 할 수 있어요" 라고 하지 않는다 (전체 점검) */
describe('Home 다가오는 방문', () => {
  // 준비: 진행 중 예약을 빼서, 홈 카드가 '방문 예정' 예약(가짜 서버 기본값 changeable=true)이 되게 한다
  it('바꿀 수 있는 방문은 내 예약으로 안내한다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations = __fake.reservations.filter((r) => r.status !== 'inProgress'); // 진행 중 카드는 영상 보기라 안내가 없다
    renderHome();
    expect(await screen.findByText('변경·취소는 "내 예약" 에서 할 수 있어요')).toBeInTheDocument();
  });

  // 준비: 모든 예약을 changeable=false(24시간 전이 지남)로 바꾼다. 확인: 기한 안내가 보이고 '내 예약' 안내는 없다
  it('기한이 지난 방문은 할 수 없다고 알린다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations = __fake.reservations.filter((r) => r.status !== 'inProgress');
    __fake.reservations.forEach((r) => (r.changeable = false));
    renderHome();
    expect(await screen.findByText(/방문 24시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요/)).toBeInTheDocument();
    expect(screen.queryByText('변경·취소는 "내 예약" 에서 할 수 있어요')).toBeNull();
  });

  // 준비: 위 테스트처럼 잠긴 방문 + 서버의 변경·취소 기한을 48시간으로(운영자가 설정을 바꾼 경우).
  // 확인: 안내가 앱 고정 24 가 아니라 서버가 준 48시간을 말한다 (문제 목록 25번)
  it('기한 안내의 시간은 서버가 내려 준 값을 쓴다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations = __fake.reservations.filter((r) => r.status !== 'inProgress');
    __fake.reservations.forEach((r) => (r.changeable = false));
    __fake.changeDeadlineHours = 48;
    renderHome();
    expect(await screen.findByText(/방문 48시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요/)).toBeInTheDocument();
    __fake.changeDeadlineHours = 24; // 뒤 테스트를 위해 되돌린다
  });
});

describe('새 홈 서비스와 탐색', () => {
  it('매장·가정·원룸 동그란 버튼을 보이고 숨긴 서비스는 홈에 노출하지 않는다', async () => {
    renderHome();
    expect(await screen.findByRole('button', { name: '원룸' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '가정' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '매장' })).toBeInTheDocument();
    for (const name of ['입주/이사 청소', '가전청소', '사무실 청소']) expect(screen.queryByText(name)).toBeNull();
    expect(screen.getByRole('button', { name: '문의' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '전체보기' })).toBeNull();
    expect(screen.queryByText('전체보기')).toBeNull();
  });
  it('원룸 선택을 예약에 전달한다', async () => {
    render(<MemoryRouter initialEntries={['/home']}><Routes>
      <Route path="/home" element={<Home />} />
      <Route path="/booking/home/frequency" element={<p>예약 주기 화면</p>} />
    </Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: '원룸' }));
    expect(await screen.findByText('예약 주기 화면')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home')).homeService).toBe('studio');
  });
  it('매장은 매장 청소 예약으로 이동한다', async () => {
    render(<MemoryRouter initialEntries={['/home']}><Routes>
      <Route path="/home" element={<Home />} />
      <Route path="/booking/store/frequency" element={<p>매장 예약 화면</p>} />
    </Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: '매장' }));
    expect(await screen.findByText('매장 예약 화면')).toBeInTheDocument();
  });
  it('문의 탭은 실제 문의 경로로 이동한다', async () => {
    render(<MemoryRouter initialEntries={['/home']}><Routes>
      <Route path="/home" element={<Home />} />
      <Route path="/settings/inquiry" element={<p>문의 화면</p>} />
    </Routes></MemoryRouter>);
    await screen.findByRole('heading', { name: '안심클린', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: '문의' }));
    expect(await screen.findByText('문의 화면')).toBeInTheDocument();
  });
});

/** 홈이 열려 있는 동안 매니저가 배정되면(서버가 고객에게 푸시) 다시 받아 '다가오는 방문' 카드가 바뀐다 */
describe('Home 갱신', () => {
  it('열려 있는 동안 푸시가 오면 다시 받아 매니저 배정을 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    // 준비: 가장 이른 예정 카드(r1)를 '배정 중' 으로 바꿔 둔다 (진행 중 카드는 뺀다 — 홈 카드가 r1 이 되게)
    const assigning = (r) => ({ ...r, status: 'assigning', rows: r.rows.map((row) => (row.label === '매니저' ? { ...row, value: '배정 후 알려드려요' } : row)) });
    const original = __fake.reservations.find((r) => r.id === 'r1');
    __fake.reservations = __fake.reservations.filter((r) => r.status !== 'inProgress').map((r) => (r.id === 'r1' ? assigning(r) : r));
    renderHome();
    expect(await screen.findByText('배정 후 알려드려요')).toBeInTheDocument();

    __fake.reservations = __fake.reservations.map((r) => (r.id === 'r1' ? original : r)); // 매니저가 콜을 수락했다
    fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} })); // 앱이 떠 있을 때 온 푸시 (pushClient.js)
    await waitFor(() => expect(screen.getByText('김미영 매니저')).toBeInTheDocument());
    expect(screen.queryByText('배정 후 알려드려요')).toBeNull();
  });
});
