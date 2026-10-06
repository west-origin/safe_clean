// 내 예약 화면 테스트: 취소 확인 시트와 탭 이동, 정기청소 전체 취소, 녹화 영상 보기, 서버의 24시간 거부 문구,
// 기한이 지난 카드의 안내 문구를 확인한다. 서버는 test/fakeHttp.js(메모리 안의 가짜 서버)로 바꾼다.
// 이 파일에는 테스트 사이에 가짜 서버 상태를 비우는 코드가 없다 — 앞 테스트에서 취소·추가한 예약이 뒤 테스트에도 남는다.
// within(요소): 그 요소 안에서만 찾는다 (같은 이름의 버튼이 카드와 시트에 모두 있을 때 구분).
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Reservations from './Reservations.jsx';
import { LOCKED_RESERVATION_ID } from '../../test/fakeHttp.js';

// 테스트 안의 await import('../../api/http.js') 도 이 가짜 모듈을 돌려주므로, 거기서 꺼낸 __fake·http 로 서버 상태를 꾸민다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// /reservations 에서 시작하는 메모리 라우터로 화면을 띄운다. 라이브 화면은 글자만 있는 대역이다
function renderScreen() {
  return render(
    <MemoryRouter initialEntries={['/reservations']}>
      <Routes>
        <Route path="/reservations" element={<Reservations />} />
        <Route path="/service/live" element={<p>라이브 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Reservations 흐름', () => {
  it('예약 취소는 확인 시트를 거쳐 취소 탭으로 옮기고, 진행 중 예약은 실시간 영상 보기로 간다', async () => {
    renderScreen();
    expect(await screen.findByRole('tab', { name: /예정/ })).toBeInTheDocument();
    const card = screen.getByRole('article', { name: '가전청소' });
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('예약을 취소할까요?');
    expect(dialog).toHaveTextContent('가전청소 · 2026년 11월 14일'); // 어느 방문인지 (전체 점검 G5)
    expect(within(dialog).queryByRole('radiogroup')).toBeNull(); // 1회 예약에는 범위 선택이 없다
    fireEvent.click(within(dialog).getByRole('button', { name: '예약 취소' }));
    expect(await screen.findByRole('tab', { name: '취소', selected: true })).toBeInTheDocument();
    expect(screen.getByRole('article', { name: '가전청소' })).toHaveTextContent('취소');

    fireEvent.click(screen.getByRole('tab', { name: /예정/ }));
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 보기' }));
    expect(await screen.findByText('라이브 화면')).toBeInTheDocument();
  });

  // 가짜 서버에 정기청소 예약(6회차)을 먼저 만들어 두고 → 1회차 카드에서 '정기청소 전체' 로 취소 →
  // 보낸 본문의 wholeSeries 와 6회차가 모두 취소 탭으로 갔는지 확인한다
  it('정기청소 회차는 "이번 회차만 / 정기청소 전체" 를 고를 수 있고 전체를 고르면 남은 회차가 모두 취소된다', async () => {
    const { __fake, http } = await import('../../api/http.js');
    await http.post('/customer/bookings', { category: 'home', frequency: 'recurring', date: '2026-11-02', time: '10:00', roadAddress: '부산광역시 중앙대로 1' }, { idempotencyKey: 'k' });
    renderScreen();
    expect(await screen.findByRole('tab', { name: '예정 8' })).toBeInTheDocument(); // Figma 예시 2 + 정기 6회차
    const cards = screen.getAllByRole('article', { name: '가정/원룸 청소' });
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
    const card = screen.getByRole('article', { name: '가전청소' });
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
    expect(card).toHaveTextContent('방문 24시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요. 1:1 문의로 알려주세요.'); // 고객센터 번호가 설정되지 않았을 때
    expect(within(card).queryByRole('button', { name: '예약 취소' })).toBeNull();
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
    expect(locked).toHaveTextContent('방문 48시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요.');
    fireEvent.click(within(screen.getByRole('article', { name: '입주(이사) 청소 · 정기 2/6회' })).getByRole('button', { name: '예약 취소' }));
    expect(screen.getByRole('dialog')).toHaveTextContent('48시간 안에 시작하는 방문은 취소할 수 없어요.');
    __fake.changeDeadlineHours = 24; // 이 파일은 가짜 서버 상태를 이어 쓰므로 되돌린다
  });
});
