// 고객 앱 홈 화면(Home.jsx) 테스트.
// ① '다가오는 방문' 카드의 안내 문구가 변경 가능 여부에 맞게 바뀌는지 ② 히어로 캐러셀이 스크롤·탭·키보드로 넘어가는지 확인한다.
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

/** 히어로 캐러셀 — 가로 트랙을 밀거나(스크롤) 탭하면 넘어가고, 점은 제자리에서 바뀐다 (2026-09-24 사용자 승인) */
describe('Home 히어로 스와이프', () => {
  // 홈을 띄우고 트랙을 찾는다. jsdom(테스트용 가짜 브라우저)은 레이아웃을 계산하지 않아 clientWidth 가 0 이므로
  // 폭을 375 로 정해 둔다 — 0 이면 화면의 onScroll 이 장 번호 계산을 건너뛴다
  const setup = async () => {
    renderHome();
    const track = await screen.findByRole('group', { name: /서비스 소개/ });
    Object.defineProperty(track, 'clientWidth', { configurable: true, value: 375 });
    return track;
  };
  // 점(CarouselDots)은 role="img" 이고 이름이 '슬라이드 n / 전체' 라 지금 장 번호를 이름으로 확인한다
  const dots = () => screen.getByRole('img', { name: /슬라이드/ });

  it('두 장이 나란히 있고, 반 넘게 밀면 다음 장이 된다', async () => {
    const track = await setup();
    expect(screen.getByText('실시간 청소')).toBeInTheDocument();
    expect(screen.getByText('일반 청소')).toBeInTheDocument(); // 옆 장이 트랙에 붙어 있다
    expect(dots()).toHaveAccessibleName('슬라이드 1 / 2');

    track.scrollLeft = 150; // 40% — 놓으면 제자리로
    fireEvent.scroll(track);
    expect(dots()).toHaveAccessibleName('슬라이드 1 / 2');
    track.scrollLeft = 260; // 70%
    fireEvent.scroll(track);
    expect(dots()).toHaveAccessibleName('슬라이드 2 / 2');
  });

  it('탭하면 트랙을 다음 장으로 스크롤하고, 마지막 장에서는 처음으로', async () => {
    const track = await setup();
    // jsdom 에는 실제 스크롤이 없어 scrollTo 를 가짜로 바꾼다: 요청한 left 를 기록하고,
    // 브라우저처럼 scrollLeft 를 옮긴 뒤 scroll 이벤트를 보낸다
    const calls = [];
    track.scrollTo = (opts) => {
      calls.push(opts.left);
      track.scrollLeft = opts.left;
      fireEvent.scroll(track);
    };
    fireEvent.click(track);
    expect(calls).toEqual([375]);
    expect(dots()).toHaveAccessibleName('슬라이드 2 / 2');
    fireEvent.click(track);
    expect(calls).toEqual([375, 0]);
    expect(dots()).toHaveAccessibleName('슬라이드 1 / 2');
    fireEvent.keyDown(track, { key: 'ArrowRight' }); // 키보드도 같은 길
    expect(calls.at(-1)).toBe(375);
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
