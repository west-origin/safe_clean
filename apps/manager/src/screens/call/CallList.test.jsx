import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import CallList from './CallList.jsx';
import CallDetail from './CallDetail.jsx';
import { TAKEN_OFFER_ID, MY_ACCEPTED_OFFER_ID, UNAVAILABLE_OFFER_ID } from '../../test/fakeHttp.js';
import { CALLS } from '../../api/mock.js';

// 매니저 콜 목록의 수락·거절 흐름 테스트: 수락 뒤 업무 상세 이동과 멱등 키, 이미 마감된 콜의 시트, 목록에서 바로 거절.

// http.js 를 가짜 서버(test/fakeHttp.js)로 바꿔 끼운다.
// 가짜 서버의 상태·요청 기록은 import 한 모듈의 __fake 로 들여다본다 (__fake.calls = 보낸 요청 목록)
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 콜 목록·콜 상세와, 이동했는지만 보려는 업무 상세 자리 화면을 메모리 라우터(주소창 없이 메모리 안에서 라우팅)에 그린다
function renderCalls(path = '/call') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/call" element={<CallList />} />
        <Route path="/call/:id" element={<CallDetail />} />
        <Route path="/job/:id" element={<p>업무 상세 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('콜 수락 (B4 §9.2-2)', () => {
  beforeEach(async () => {
    // 가짜 서버의 콜 목록은 테스트 파일 안에서 공유된다 → 매 테스트 처음 상태로
    const { __fake } = await import('../../api/http.js');
    __fake.offers = CALLS.calls.map((c) => ({ ...c, expiresAt: new Date(Date.now() + 272_000).toISOString() }));
  });

  // 첫 카드의 수락 → 업무 상세 자리 화면이 보이고, 요청 기록의 accept 경로와 멱등 키(가짜 서버는 늘 'key-1')를 확인한다
  it('수락하면 서버가 준 jobId 의 업무 상세로 가고, 멱등 키를 보낸다', async () => {
    const { __fake } = await import('../../api/http.js');
    renderCalls();
    const cards = await screen.findAllByRole('button', { name: '수락' });
    fireEvent.click(cards[0]);
    expect(await screen.findByText('업무 상세 화면')).toBeInTheDocument();
    const accept = __fake.calls.find((c) => c.method === 'POST' && c.path.endsWith('/accept'));
    expect(accept.path).toBe('/manager/offers/call-1/accept');
    expect(accept.idempotencyKey).toBe('key-1');
  });

  // 첫 콜 id 를 TAKEN_OFFER_ID 로 바꿔 두면 가짜 서버가 수락을 OFFER_TAKEN 으로 거부하고 그 콜을 목록에서 뺀다
  // → 마감 시트가 뜨고, 확인을 누르면 다시 받은 목록에 카드가 1장만 남는지 본다
  it('이미 마감된 콜이면 시트를 보여주고, 확인하면 목록을 새로 받는다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0] = { ...__fake.offers[0], id: TAKEN_OFFER_ID };
    renderCalls();
    expect(await screen.findAllByRole('button', { name: '수락' })).toHaveLength(2);
    fireEvent.click(screen.getAllByRole('button', { name: '수락' })[0]);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('이미 마감된 요청이에요');
    // within(dialog): 그 시트 안에서만 찾는다. waitFor: 조건이 맞을 때까지 되풀이해 확인한다
    fireEvent.click(within(dialog).getByRole('button', { name: '확인' }));
    await waitFor(() => expect(screen.getAllByRole('button', { name: '수락' })).toHaveLength(1)); // 마감된 콜은 빠졌다
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.queryByText('업무 상세 화면')).toBeNull();
  });

  // 정지된 매니저: 서버는 정지 전에 받은 콜도 싣지 않고(calls = [], 문제 목록 6번) 정지 안내를 availability 로 준다 →
  // 수락할 수 없는 카드 대신 빈 상태와 정지 안내 카드가 보인다
  it('정지된 매니저의 콜 탭은 카드 없이 정지 안내를 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.override = (method, path) => (method === 'GET' && path === '/manager/offers'
      ? { tabBadge: 0, calls: [], availability: { status: '콜 배정 정지 중', detail: '10월 3일 까지 콜을 받을 수 없어요' }, approved: false }
      : undefined);
    try {
      renderCalls();
      expect(await screen.findByText('콜 배정 정지 중')).toBeInTheDocument();
      expect(screen.getByText('10월 3일 까지 콜을 받을 수 없어요')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '수락' })).toBeNull();
    } finally {
      __fake.override = null; // 같은 파일의 다음 테스트가 평소 응답을 받게
    }
  });

  // 콜을 받은 뒤 그 시간에 휴무를 넣었다: 서버가 INVALID_STATE(근무 일정 안내)로 거부하면 마감 시트가 아니라 서버 문구를 보이고,
  // 업무 상세로 가지 않으며 카드는 남는다 (문제 목록 7번). 콜 상세에서 수락해도 같은 문구
  it('근무 일정과 맞지 않는 콜의 수락은 서버 문구를 보이고 카드를 남긴다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0] = { ...__fake.offers[0], id: UNAVAILABLE_OFFER_ID };
    renderCalls();
    fireEvent.click((await screen.findAllByRole('button', { name: '수락' }))[0]);
    expect(await screen.findByRole('alert')).toHaveTextContent('근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(screen.queryByText('업무 상세 화면')).toBeNull();
    expect(screen.getAllByRole('button', { name: '수락' })).toHaveLength(2);
  });

  it('콜 상세에서도 근무 일정과 맞지 않는 콜의 수락은 서버 문구를 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0] = { ...__fake.offers[0], id: UNAVAILABLE_OFFER_ID };
    renderCalls(`/call/${UNAVAILABLE_OFFER_ID}`);
    fireEvent.click(await screen.findByRole('button', { name: '수락' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  // 두 번째 카드의 거절 → 시트에서 '개인 사정' 선택 → 거절하기 → 시트가 닫히고, reject 요청의 경로·본문(사유)을 확인한다
  it('목록의 거절은 상세를 거치지 않고 시트에서 사유를 골라 서버에 보낸다', async () => {
    const { __fake } = await import('../../api/http.js');
    renderCalls();
    const rejects = await screen.findAllByRole('button', { name: '거절' });
    fireEvent.click(rejects[1]); // call-2
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(await within(dialog).findByRole('radio', { name: '개인 사정' })); // 사유는 시트를 열 때 받는다
    fireEvent.click(within(dialog).getByRole('button', { name: '거절하기' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    const reject = __fake.calls.find((c) => c.method === 'POST' && c.path.endsWith('/reject'));
    expect(reject).toMatchObject({ path: '/manager/offers/call-2/reject', body: { reason: '개인 사정' } });
  });

  // 거절 시트를 연 사이 다른 매니저가 수락했다: 서버가 OFFER_TAKEN 으로 답하면 거절 시트 대신 마감 시트가 뜨고,
  // 확인하면 그 콜이 빠진 목록을 다시 받는다 (문제 목록 10번 — 예전 서버는 200 이라 이 분기가 타지 않았다)
  it('거절하는 사이 마감된 콜이면 마감 시트를 보여 주고, 확인하면 목록을 새로 받는다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[1] = { ...__fake.offers[1], id: TAKEN_OFFER_ID };
    renderCalls();
    fireEvent.click((await screen.findAllByRole('button', { name: '거절' }))[1]);
    const sheet = await screen.findByRole('dialog');
    fireEvent.click(await within(sheet).findByRole('radio', { name: '개인 사정' }));
    fireEvent.click(within(sheet).getByRole('button', { name: '거절하기' }));
    const closed = await screen.findByRole('dialog', { name: '이미 마감된 요청이에요' });
    fireEvent.click(within(closed).getByRole('button', { name: '확인' }));
    await waitFor(() => expect(screen.getAllByRole('button', { name: '거절' })).toHaveLength(1));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  // 수락 응답이 끊겨 내가 이미 수락한 콜을 거절했다: 서버 문구('이미 수락한 콜이에요 …')를 거절 시트에 그대로 보여 준다
  // (예전에는 늘 '거절하지 못했어요. 다시 시도해주세요.' 라 다시 눌러도 같은 오류였다)
  it('내가 이미 수락한 콜을 거절하면 서버 문구를 보여 준다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0] = { ...__fake.offers[0], id: MY_ACCEPTED_OFFER_ID };
    renderCalls();
    fireEvent.click((await screen.findAllByRole('button', { name: '거절' }))[0]);
    const sheet = await screen.findByRole('dialog');
    fireEvent.click(await within(sheet).findByRole('radio', { name: '개인 사정' }));
    fireEvent.click(within(sheet).getByRole('button', { name: '거절하기' }));
    expect(await within(sheet).findByRole('alert')).toHaveTextContent('이미 수락한 콜이에요. 일정에서 확인해주세요');
  });

  // 콜 상세에서 거절해도 같다: 마감 오류면 마감 시트, 확인하면 콜 목록으로
  it('콜 상세의 거절이 마감 오류면 마감 시트를 보여 주고 확인하면 목록으로 간다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0] = { ...__fake.offers[0], id: TAKEN_OFFER_ID };
    renderCalls(`/call/${TAKEN_OFFER_ID}?reject=1`); // 거절 시트를 연 채로 시작
    const sheet = await screen.findByRole('dialog');
    fireEvent.click(await within(sheet).findByRole('radio', { name: '개인 사정' }));
    fireEvent.click(within(sheet).getByRole('button', { name: '거절하기' }));
    const closed = await screen.findByRole('dialog', { name: '이미 마감된 요청이에요' });
    fireEvent.click(within(closed).getByRole('button', { name: '확인' }));
    expect(await screen.findByRole('heading', { name: '새로운 청소 요청' })).toBeInTheDocument();
  });
});
