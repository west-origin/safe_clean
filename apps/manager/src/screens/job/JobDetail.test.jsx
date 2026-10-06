// 매니저 업무 상세(JobDetail) 화면의 막다른 길 방지를 검증한다: 진행 중·일시정지 작업이면 업무 화면으로 가는 버튼,
// 방문 취소 사유 조회가 실패해도 시트 안에서 다시 시도할 수 있는지. (방문 취소 흐름 자체는 JobCancel.test.jsx)
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobDetail from './JobDetail.jsx';

// api/http.js 를 test/fakeHttp.js 의 가짜 서버로 바꾼다. 가짜 서버 상태(__fake)는 이 파일의 테스트끼리 공유된다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// /job/:id 로 업무 상세를 연다. MemoryRouter 는 주소창 없이 경로를 흉내 내는 테스트용 라우터이고,
// '/job/in-progress'·'/job/paused' 자리에는 이동을 확인할 '진행 화면'·'일시정지 화면' 문구만 둔다
function renderAt(id) {
  return render(
    <MemoryRouter initialEntries={[`/job/${id}`]}>
      <Routes>
        <Route path="/job/:id" element={<JobDetail />} />
        <Route path="/job/in-progress" element={<p>진행 화면</p>} />
        <Route path="/job/paused" element={<p>일시정지 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Manager/Job/Detail', () => {
  // 테스트마다 바꾼 가짜 서버 설정(상세 덮어쓰기·한 번 실패)을 되돌려 다음 테스트에 새지 않게 한다
  afterEach(async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.jobDetail = {};
    __fake.jobError = null;
    __fake.failOnce.clear();
  });

  it('진행 중인 작업이면 진행 화면으로 돌아가는 버튼이 있다 — 바디캠 끊김 푸시가 막다른 곳이 아니게 (자체 점검 #32)', async () => {
    // 준비: 가짜 서버의 상세 응답 status 를 inProgress 로 덮어쓴다 → 확인: 버튼을 누르면 진행 화면으로 간다
    const { __fake } = await import('../../api/http.js');
    __fake.jobDetail = { status: 'inProgress' };
    renderAt('job-1');
    fireEvent.click(await screen.findByRole('button', { name: '업무 화면으로 돌아가기' }));
    expect(await screen.findByText('진행 화면')).toBeInTheDocument();
  });

  it('일시정지된 작업이면 같은 버튼이 일시정지 화면으로 보낸다 — 홈 카드와 같게 (문제 목록 11번)', async () => {
    // 서버(JobService.badge)는 일시정지를 'paused' 로 따로 준다. 예전에는 이 상태에 하단 버튼이 없어 재개 화면으로 갈 길이 없었다
    const { __fake } = await import('../../api/http.js');
    __fake.jobDetail = { status: 'paused' };
    renderAt('job-1');
    fireEvent.click(await screen.findByRole('button', { name: '업무 화면으로 돌아가기' }));
    expect(await screen.findByText('일시정지 화면')).toBeInTheDocument();
  });

  it('취소 사유를 못 받으면 시트에 오류와 다시 시도가 보이고, 다시 받으면 고를 수 있다 (자체 점검 #45)', async () => {
    // 준비: 사유 목록 요청만 한 번 NETWORK 로 실패시킨다(failOnce) → 시트를 열면 '다시 시도' 가 보이고,
    // 누르면 두 번째 요청은 성공해 사유 radio 가 나타난다
    const { __fake } = await import('../../api/http.js');
    __fake.failOnce.add('GET /manager/cancel-reasons');
    renderAt('job-1');
    fireEvent.click(await screen.findByRole('button', { name: '방문 취소' }));
    const dialog = screen.getByRole('dialog');
    fireEvent.click(await within(dialog).findByRole('button', { name: '다시 시도' }));
    expect((await within(dialog).findAllByRole('radio')).length).toBeGreaterThan(0);
  });

  it('보고 있는 동안 푸시가 오면 다시 받아 고객이 바꾼 일정을 보인다', async () => {
    // 준비: 상세를 연 뒤 가짜 서버의 시작 시각을 바꾼다(고객이 일정을 변경) → 푸시가 오면 화면이 새 값으로 바뀐다
    const { __fake } = await import('../../api/http.js');
    renderAt('job-1');
    expect(await screen.findByText('10:00 시작')).toBeInTheDocument();
    __fake.jobDetail = { startLabel: '11:00 시작' };
    fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} })); // 앱이 떠 있을 때 온 푸시 (pushClient.js)
    await waitFor(() => expect(screen.getByText('11:00 시작')).toBeInTheDocument());
  });

  it('보고 있는 동안 고객이 취소하면(서버 409) 푸시 뒤 옛 버튼 대신 서버 문구를 보인다', async () => {
    // 조용한 갱신은 실패를 버린다 — 대상이 사라진 409·404 만 화면을 다시 받아 오류 화면으로 바꾼다
    const { __fake } = await import('../../api/http.js');
    renderAt('job-1');
    expect(await screen.findByRole('button', { name: '방문 취소' })).toBeInTheDocument();
    __fake.jobError = { status: 409, code: 'INVALID_STATE', message: '취소된 예약이에요' };
    fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} }));
    expect(await screen.findByText('취소된 예약이에요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '방문 취소' })).toBeNull();
  });
});

