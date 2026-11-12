// 고객 앱 홈 화면(Home.jsx) 테스트.
// 예약 상태·기한 안내 유지, 청소 진입점 분리, 문의 탭 경로를 확인한다.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
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

beforeEach(() => vi.setSystemTime(new Date('2026-11-01T00:00:00+09:00')));
afterEach(() => vi.useRealTimers());

describe('다가오는 방문 요약', () => {
  it('지난 날짜의 예약을 제외하고 다음 매칭 완료 예약을 보여준다', async () => {
    const { __fake } = await import('../../api/http.js');
    const original = __fake.reservations;
    const base = original.find((r) => r.id === 'r1');
    __fake.reservations = [
      { ...base, id: 'past', date: '2026-10-31', addressLabel: '지난 예약' },
      { ...base, id: 'matching', date: '2026-11-01', status: 'assigning', addressLabel: '매칭 중 예약' },
      { ...base, id: 'future', date: '2026-11-02', addressLabel: '다음 예약' },
    ];
    try {
      renderHome();
      expect(await screen.findByRole('button', { name: /다음 예약.*매칭 완료/ })).toBeInTheDocument();
      expect(screen.queryByText('지난 예약')).toBeNull();
      expect(screen.queryByText('매칭 중 예약')).toBeNull();
    } finally { __fake.reservations = original; }
  });

  it('지난 예약만 있거나 예약이 없으면 카드 안에 빈 예약 안내를 보여준다', async () => {
    const { __fake } = await import('../../api/http.js');
    const original = __fake.reservations;
    __fake.reservations = [{ ...original.find((r) => r.id === 'r1'), date: '2026-10-31', addressLabel: '지난 예약' }];
    try {
      renderHome();
      expect(await screen.findByText('다가오는 예약이 없어요')).toBeInTheDocument();
      expect(screen.queryByText('지난 예약')).toBeNull();
      __fake.reservations = [];
      fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} }));
      await waitFor(() => expect(screen.getByText('다가오는 예약이 없어요')).toBeInTheDocument());
    } finally { __fake.reservations = original; }
  });

  it('장소 별칭·방문일·매칭 완료만 표시하고 카드를 누르면 내 예약으로 이동한다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations = __fake.reservations.filter((r) => r.status !== 'inProgress');
    const r = __fake.reservations.find((r) => r.id === 'r1');
    __fake.addresses = [{ id: 'place', label: '부모님 댁', roadAddress: r.rows.find((row) => row.label === '주소').value, detailAddress: '' }];
    render(<MemoryRouter initialEntries={['/home']}><Routes>
      <Route path="/home" element={<Home />} />
      <Route path="/reservations" element={<p>내 예약 화면</p>} />
    </Routes></MemoryRouter>);
    const card = await screen.findByRole('button', { name: /부모님 댁.*매칭 완료/ });
    expect(card).toHaveTextContent('2026년 11월 1일');
    expect(card).not.toHaveTextContent('매니저');
    expect(card).not.toHaveTextContent('방문 예정');
    expect(card).not.toHaveTextContent('문의');
    fireEvent.click(card);
    expect(await screen.findByText('내 예약 화면')).toBeInTheDocument();
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
  it('분해청소·수리·사진 문의는 각각 가전청소 예약·수리·1:1 문의로 이동한다', async () => {
    for (const [name, path, text] of [
      ['분해청소', '/booking/appliance/frequency', '가전청소 예약 화면'],
      ['수리', '/repair', '수리 화면'],
      [/사진 문의/, '/settings/inquiry', '문의 화면'],
    ]) {
      const { unmount } = render(<MemoryRouter initialEntries={['/home']}><Routes>
        <Route path="/home" element={<Home />} />
        <Route path={path} element={<p>{text}</p>} />
      </Routes></MemoryRouter>);
      fireEvent.click(await screen.findByRole('button', { name }));
      expect(await screen.findByText(text)).toBeInTheDocument();
      unmount();
    }
  });
  it('사진 문의에는 무료 배지가 붙는다', async () => {
    renderHome();
    expect(await screen.findByRole('button', { name: '무료 사진 문의' })).toBeInTheDocument();
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
    await screen.findByRole('heading', { name: '안심클린', level: 1 });
    expect(screen.queryByText('매니저님을 매칭 중이에요')).toBeNull();

    __fake.reservations = __fake.reservations.map((r) => (r.id === 'r1' ? original : r)); // 매니저가 콜을 수락했다
    fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} })); // 앱이 떠 있을 때 온 푸시 (pushClient.js)
    await waitFor(() => expect(screen.getByText('매칭 완료')).toBeInTheDocument());
    expect(screen.queryByText('매니저님을 매칭 중이에요')).toBeNull();
    expect(screen.getByText('매칭 완료')).toBeInTheDocument();
  });
});
