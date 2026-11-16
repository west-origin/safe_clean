// 내 예약 화면 테스트: 취소 확인 시트와 탭 이동, 정기청소 전체 취소, 녹화 영상 보기, 서버의 24시간 거부 문구,
// 기한이 지난 카드의 안내 문구를 확인한다. 서버는 test/fakeHttp.js(메모리 안의 가짜 서버)로 바꾼다.
// 이 파일에는 테스트 사이에 가짜 서버 상태를 비우는 코드가 없다 — 앞 테스트에서 취소·추가한 예약이 뒤 테스트에도 남는다.
// within(요소): 그 요소 안에서만 찾는다 (같은 이름의 버튼이 카드와 시트에 모두 있을 때 구분).
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Reservations from './Reservations.jsx';
import CustomerFooter from '../CustomerFooter.jsx';
import { LOCKED_RESERVATION_ID } from '../../test/fakeHttp.js';

// 테스트 안의 await import('../../api/http.js') 도 이 가짜 모듈을 돌려주므로, 거기서 꺼낸 __fake·http 로 서버 상태를 꾸민다
vi.mock('../../api/http.js', async () => {
  const module = (await import('../../test/fakeHttp.js')).fakeHttpModule();
  const labels = { r1: '우리집', r0: '자취방', r2: '사무실', r3: '사무실' };
  module.__fake.reservations.forEach((r) => { r.addressLabel = labels[r.id]; });
  return module;
});

// /reservations 에서 시작하는 메모리 라우터로 화면을 띄운다. 라이브 화면은 글자만 있는 대역이다
function renderScreen(focusBookingId) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/reservations', state: { focusBookingId } }]}>
      <Routes>
        <Route path="/reservations" element={<Reservations />} />
        <Route path="/service/live" element={<p>라이브 화면</p>} />
        <Route path="/service/photos" element={<p>사진 비교 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Reservations 흐름', () => {
  it('하단 내 예약 진입 시 목록을 숨기고 날짜 선택 후 표시하며 재진입하면 초기화한다', async () => {
    const { __fake } = await import('../../api/http.js');
    const now = new Date();
    const month = now.getMonth() + 1;
    const date = `${now.getFullYear()}-${String(month).padStart(2, '0')}-19`;
    __fake.reservations.push({ id: 'footer-calendar', tab: 'upcoming', category: 'test', title: '날짜 선택 예약', status: 'assigning', date, time: '10:00', rows: [] });
    try {
      render(<MemoryRouter initialEntries={['/home']}><Routes>
        <Route path="/home" element={<CustomerFooter active="home" />} />
        <Route path="/reservations" element={<Reservations />} />
      </Routes></MemoryRouter>);
      fireEvent.click(screen.getByRole('button', { name: '내 예약' }));
      await screen.findByRole('heading', { name: '내 예약을 확인해 주세요.' });
      expect(screen.queryByRole('article')).toBeNull();
      expect(screen.queryByRole('heading', { name: '예약 달력' })).toBeNull();
      expect(screen.queryByRole('button', { name: '선택 해제' })).toBeNull();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 19일, 예약 있음` }));
      expect(screen.getByRole('article', { name: '날짜 선택 예약' })).toBeInTheDocument();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 19일, 예약 있음` }));
      expect(screen.queryByRole('article')).toBeNull();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 19일, 예약 있음` }));
      fireEvent.click(screen.getByRole('button', { name: '내 예약' }));
      expect(screen.queryByRole('article')).toBeNull();
    } finally {
      __fake.reservations = __fake.reservations.filter((r) => r.id !== 'footer-calendar');
    }
  });

  it('홈에서 지정한 예약 카드에 로딩 후 포커스한다', async () => {
    renderScreen('r2');
    const card = await screen.findByRole('article', { name: '사무실' });
    // 포커스는 카드가 그려진 뒤 effect 에서 옮긴다 — 느린 CI 에서는 한 박자 늦을 수 있어 기다린다
    await waitFor(() => expect(card).toHaveFocus());
    expect(screen.getByRole('tab', { name: /예정/, selected: true })).toBeInTheDocument();
  });

  it('홈에서 선택한 예약 날짜는 취소·완료·예정 탭을 오가도 유지한다', async () => {
    const { __fake } = await import('../../api/http.js');
    const target = __fake.reservations.find((r) => r.id === 'r2');
    const another = { ...target, id: 'other-date', addressLabel: '다른 날짜 예약', date: '2027-01-25' };
    __fake.reservations.push(another);
    try {
      renderScreen('r2');
      await screen.findByRole('article', { name: '사무실' });
      for (const name of ['취소', '완료']) {
        fireEvent.click(screen.getByRole('tab', { name }));
        expect(screen.getByRole('gridcell', { selected: true })).toBeInTheDocument();
      }
      fireEvent.click(screen.getByRole('tab', { name: /예정/ }));
      expect(screen.getByRole('article', { name: '사무실' })).toBeInTheDocument();
      expect(screen.queryByRole('article', { name: '다른 날짜 예약' })).toBeNull();
      expect(screen.queryByRole('article', { name: '우리집' })).toBeNull();
    } finally {
      __fake.reservations = __fake.reservations.filter((r) => r.id !== another.id);
    }
  });

  it('2026-11-03 시안: 제목·탭, 카드는 매장·가정·원룸 이름 + 파란 일정 + 매칭 알약 + 매니저 + 진행 상태 + 시작·종료, 결제 버튼 없음', async () => {
    renderScreen();
    expect(await screen.findByRole('heading', { name: '내 예약을 확인해 주세요.' })).toBeInTheDocument();
    const house = screen.getByRole('article', { name: '우리집' }); // r1: homeType house, 매니저 수락
    expect(house).toHaveTextContent('2026년 11월 1일 (일) 오전 9시');
    expect(house).toHaveTextContent('매칭 완료');
    expect(house).toHaveTextContent('김미영 매니저');
    expect(house).toHaveTextContent('11.1 (일) 09:00');
    expect(house).toHaveTextContent('11.1 (일) 12:00');
    expect(within(house).getByRole('button', { name: '예약 변경' })).toBeInTheDocument();
    const studio = screen.getByRole('article', { name: '자취방' }); // r0: 진행 중
    expect(studio).toHaveTextContent('청소 중');
    const matching = screen.getByRole('article', { name: '사무실' }); // 매칭 전: 매칭 중 알약 + 한 가지 문구, 진행 상태 없음
    expect(matching).toHaveTextContent('매칭 중');
    expect(matching).toHaveTextContent('매니저님을 매칭 중이에요');
    expect(matching).not.toHaveTextContent('예약 완료');
    expect(screen.queryByRole('button', { name: /결제/ })).toBeNull();
  });

  it('완료 탭: 매칭 알약 없이 실제 방문 시간·종료 시간', async () => {
    renderScreen();
    fireEvent.click(await screen.findByRole('tab', { name: '완료' }));
    const card = screen.getByRole('article', { name: '사무실' }); // r3: startedAt 14:05, endedAt 16:20
    expect(card).not.toHaveTextContent('매칭 완료');
    expect(card).toHaveTextContent('시작 시간');
    expect(card).toHaveTextContent('8.3 (월) 14:05');
    expect(card).toHaveTextContent('종료 시간');
    expect(card).toHaveTextContent('8.3 (월) 16:20');
    expect(card).not.toHaveTextContent('방문 시작');
  });

  it('기한이 지난 예약은 버튼 대신 1:1 문의 안내', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations.push({ id: 'locked', tab: 'upcoming', category: 'store', title: '매장 청소', status: 'scheduled', date: '2030-01-02', time: '10:00', changeable: false, rows: [{ label: '일정', value: '2030년 1월 2일' }, { label: '주소', value: '-' }, { label: '매니저', value: '김미영 매니저' }] });
    renderScreen();
    const card = await screen.findByRole('article', { name: '매장' });
    expect(card).toHaveTextContent('당일 청소 예약 취소는 문의를 통해 가능해요');
    expect(within(card).queryByRole('button', { name: '예약 취소' })).toBeNull();
    __fake.reservations.splice(__fake.reservations.findIndex((r) => r.id === 'locked'), 1); // 가짜 서버 상태를 이어 쓰는 아래 테스트의 '예정 8' 건수를 지킨다
  });

  it('녹화가 없는 완료 예약에도 사진 비교 진입점이 있다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations.push({ id: 'done-no-video', tab: 'done', category: 'home', title: '사진 확인용 청소', status: 'done', recordingReady: false, rows: [] });
    renderScreen();
    fireEvent.click(await screen.findByRole('tab', { name: '완료' }));
    const card = screen.getByRole('article', { name: '가정/원룸' }); // homeType 없는 옛 home 예약
    expect(within(card).queryByRole('button', { name: '녹화 영상 보기' })).toBeNull();
    fireEvent.click(within(card).getByRole('button', { name: '청소 전·후 사진 보기' }));
    expect(await screen.findByText('사진 비교 화면')).toBeInTheDocument();
  });
  it('예약 취소는 확인 시트를 거쳐 취소 탭으로 옮기고, 진행 중 예약은 실시간 영상 보기로 간다', async () => {
    renderScreen();
    expect(await screen.findByRole('tab', { name: /예정/ })).toBeInTheDocument();
    const card = screen.getByRole('article', { name: '사무실' });
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('예약을 취소할까요?');
    expect(dialog).toHaveTextContent('가전청소 · 2026년 11월 14일'); // 어느 방문인지 (전체 점검 G5)
    expect(within(dialog).queryByRole('radiogroup')).toBeNull(); // 1회 예약에는 범위 선택이 없다
    fireEvent.click(within(dialog).getByRole('button', { name: '예약 취소' }));
    expect(await screen.findByRole('tab', { name: '취소', selected: true })).toBeInTheDocument();
    expect(screen.getByRole('article', { name: '사무실' })).toHaveTextContent('취소');

    fireEvent.click(screen.getByRole('tab', { name: /예정/ }));
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 보기' }));
    expect(await screen.findByText('라이브 화면')).toBeInTheDocument();
  });

  // 가짜 서버에 정기청소 예약(6회차)을 먼저 만들어 두고 → 1회차 카드에서 '정기청소 전체' 로 취소 →
  // 보낸 본문의 wholeSeries 와 6회차가 모두 취소 탭으로 갔는지 확인한다
  it('정기청소 회차는 "이번 회차만 / 정기청소 전체" 를 고를 수 있고 전체를 고르면 남은 회차가 모두 취소된다', async () => {
    const { __fake, http } = await import('../../api/http.js');
    // 매장 정기청소(매월 6회차) — 가정·원룸은 요금표 전환(2026-11-13)으로 주 N회 요금제라 예전 매월 규칙은 매장으로 본다
    await http.post('/customer/bookings', { category: 'store', frequency: 'recurring', date: '2026-11-02', time: '10:00', roadAddress: '부산광역시 중앙대로 1' }, { idempotencyKey: 'k' });
    renderScreen();
    expect(await screen.findByRole('tab', { name: '예정 8' })).toBeInTheDocument(); // Figma 예시 2 + 정기 6회차
    const cards = screen.getAllByRole('article', { name: '매장' });
    // 예정 탭은 가까운 순: 진행 중(9/19) → r1(11/1) → 정기 1회차(11/2) …
    const seriesCard = cards.find((c) => within(c).queryByText('2026년 11월 2일 (월) 오전 10시'));
    fireEvent.click(within(seriesCard).getByRole('button', { name: '예약 취소' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(within(dialog).getByRole('radio', { name: '정기청소 전체' }));
    expect(dialog).toHaveTextContent('24시간 안에 시작하는 방문은 취소할 수 없어요.');
    // 버튼이 무엇을 취소하는지 그대로 말한다 (사용성 검토 2026-09-22)
    fireEvent.click(within(dialog).getByRole('button', { name: '정기청소 전체 취소하기' }));
    expect(await screen.findByRole('tab', { name: '취소', selected: true })).toBeInTheDocument();
    const cancelCall = __fake.calls.findLast((c) => c.method === 'POST' && c.path.endsWith('/cancel'));
    expect(cancelCall.body).toEqual({ wholeSeries: true });
    expect(__fake.reservations.filter((r) => r.seriesId && r.tab === 'cancelled')).toHaveLength(6);
  });

  it('완료 탭에서 녹화본이 준비된 예약만 "녹화 영상 보기" 가 있고, 누르면 재생 화면으로 간다 (B5)', async () => {
    renderScreen();
    fireEvent.click(await screen.findByRole('tab', { name: '완료' }));
    const card = screen.getByRole('article', { name: '사무실' });
    fireEvent.click(within(card).getByRole('button', { name: '녹화 영상 보기' }));
    expect(await screen.findByText('라이브 화면')).toBeInTheDocument();
  });

  // 가짜 서버가 취소를 거부하는 id(LOCKED_RESERVATION_ID)의 예약을 목록 맨 앞에 넣고 → 취소를 누르면
  // 시트 안에 서버 문구(role="alert")가 뜨고 탭은 예정에 그대로인지 확인한다
  it('서버가 24시간 규칙으로 거부하면 그 문구를 시트에 보여준다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations.unshift({ id: LOCKED_RESERVATION_ID, tab: 'upcoming', category: 'move-in', title: '입주(이사) 청소', status: 'scheduled', date: '2026-09-20', time: '10:00', series: false, rows: [] });
    renderScreen();
    const card = await screen.findByRole('article', { name: '입주(이사) 청소' });
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '예약 취소' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('방문 24시간 전까지만 취소할 수 있어요');
    expect(screen.getByRole('tab', { name: /예정/, selected: true })).toBeInTheDocument();
  });

  // changeable: false 인 예약을 넣으면 → 카드에 '할 수 없어요' 안내와 연락 방법이 보이고 취소 버튼은 없어야 한다
  it('기한이 지난 카드는 되는 것처럼 읽히지 않게 — 안 된다는 것과 연락 방법 (직접 사용 점검 U2)', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.reservations.unshift({ id: 'r-past-deadline', tab: 'upcoming', category: 'office', title: '사무실 청소', status: 'assigning', date: '2026-09-20', time: '10:00', series: false, changeable: false, rows: [] });
    renderScreen();
    const card = await screen.findByRole('article', { name: '사무실 청소' });
    expect(card).not.toHaveTextContent('당일 청소 예약 취소는 문의를 통해 가능해요');
    expect(within(card).getByRole('button', { name: '예약 취소' })).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: '예약 변경' })).toBeInTheDocument();
  });

  // 운영자가 변경·취소 기한을 48시간으로 바꿨다(서버 changeDeadlineHours = 48). 잠긴 정기 회차 카드와 아직 취소할 수 있는 회차 카드를 넣고 →
  // 잠긴 카드 안내와 취소 시트의 정기 회차 안내가 앱 고정 24 가 아니라 48시간을 말하는지 본다 (문제 목록 25번)
  it('잠긴 카드와 정기 회차 취소 시트의 기한 시간은 서버가 내려 준 값을 쓴다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.changeDeadlineHours = 48;
    __fake.reservations.unshift(
      { id: 'r-48-locked', tab: 'upcoming', category: 'move-in', title: '입주(이사) 청소 · 정기 1/6회', status: 'assigning', date: '2026-09-21', time: '10:00', series: true, seriesId: 's-48', changeable: false, rows: [] },
      { id: 'r-48-open', tab: 'upcoming', category: 'move-in', title: '입주(이사) 청소 · 정기 2/6회', status: 'assigning', date: '2026-10-21', time: '10:00', series: true, seriesId: 's-48', changeable: true, rows: [] },
    );
    renderScreen();
    const locked = await screen.findByRole('article', { name: '입주(이사) 청소 · 정기 1/6회' });
    expect(within(locked).getByRole('button', { name: '예약 변경' })).toBeInTheDocument();
    fireEvent.click(within(screen.getByRole('article', { name: '입주(이사) 청소 · 정기 2/6회' })).getByRole('button', { name: '예약 취소' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('48시간 안에 시작하는 방문은 취소할 수 없어요.');
    __fake.changeDeadlineHours = 24; // 이 파일은 가짜 서버 상태를 이어 쓰므로 되돌린다
  });
  it('작업 시작만으로 시청 버튼을 보이지 않고 영상 시작 후 실시간 표시와 버튼을 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    const r = { id: 'video-gate', tab: 'upcoming', category: 'test', title: '영상 테스트', status: 'inProgress', date: '2026-11-04', time: '10:00', liveStarted: false, rows: [] };
    __fake.reservations.push(r);
    renderScreen();
    let card = await screen.findByRole('article', { name: '영상 테스트' });
    expect(within(card).queryByRole('button', { name: '실시간 영상 보기' })).toBeNull();
    expect(card).toHaveTextContent('매칭 완료');
    r.liveStarted = true;
    fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} }));
    expect(await within(card).findByRole('button', { name: '실시간 영상 보기' })).toBeInTheDocument();
    card = screen.getByRole('article', { name: '영상 테스트' });
    expect(card).toHaveTextContent('실시간');
    expect(card).not.toHaveTextContent('매칭 완료');
    expect(card).toHaveTextContent('시작 시간');
    expect(card).not.toHaveTextContent('방문 예정');
    __fake.reservations.splice(__fake.reservations.indexOf(r), 1);
  });

  it('일정 날짜에 점을 표시하고 선택한 날의 여러 예약만 보여주며 전체 보기로 해제한다', async () => {
    const { __fake } = await import('../../api/http.js');
    const now = new Date();
    const month = now.getMonth() + 1;
    const prefix = `${now.getFullYear()}-${String(month).padStart(2, '0')}`;
    const fixtures = ['A', 'B', 'C'].map((name, i) => ({ id: `calendar-${name}`, tab: 'upcoming', category: 'calendar-test', title: `달력 테스트 ${name}`, status: 'assigning', date: `${prefix}-${i < 2 ? '19' : '20'}`, time: '10:00', rows: [] }));
    __fake.reservations.push(...fixtures);
    try {
      renderScreen();
      fireEvent.click(await screen.findByRole('gridcell', { name: `${month}월 19일, 예약 있음` }));
      expect(screen.getByRole('article', { name: '달력 테스트 A' })).toBeInTheDocument();
      expect(screen.getByRole('article', { name: '달력 테스트 B' })).toBeInTheDocument();
      expect(screen.queryByRole('article', { name: '달력 테스트 C' })).toBeNull();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 20일, 예약 있음` }));
      expect(screen.getByRole('article', { name: '달력 테스트 C' })).toBeInTheDocument();
      expect(screen.queryByRole('article', { name: '달력 테스트 A' })).toBeNull();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 18일` }));
      expect(screen.getByText('선택한 날짜에 예약이 없어요')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('gridcell', { name: `${month}월 18일` }));
      expect(screen.getByRole('article', { name: '달력 테스트 A' })).toBeInTheDocument();
      expect(screen.getByRole('article', { name: '달력 테스트 C' })).toBeInTheDocument();
    } finally {
      __fake.reservations = __fake.reservations.filter((r) => !r.id.startsWith('calendar-'));
    }
  });

});
