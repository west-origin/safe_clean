// 매니저 업무 상세(JobDetail) › '방문 취소' 시트와 방문일 전 안내를 검증한다.
// 서버 대신 test/fakeHttp.js 의 가짜 서버를 쓴다. 'job-1' 은 오늘 작업이고, UNSTARTABLE_JOB_ID('job-tomorrow')는
// 방문일 전 작업이라 가짜 서버가 today: false·빈 청소 범위를 주고, 취소·시작은 409 로 거부하며 연락처는 404 로 답한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobDetail from './JobDetail.jsx';
import { session } from '../../api/session.js';

// 화면이 부르는 api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다. 가짜 서버 상태(__fake)는 이 파일의 테스트끼리 공유된다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// /job/:id 로 업무 상세를 연다. MemoryRouter 는 주소창 없이 메모리 안에서 경로를 흉내 내는 테스트용 라우터다.
// 취소가 성공하면 화면이 '/' 로 가므로, 그 자리에 '홈 화면' 문구만 두어 이동을 확인한다.
function renderDetail(id = 'job-1') {
  return render(
    <MemoryRouter initialEntries={[`/job/${id}`]}>
      <Routes>
        <Route path="/job/:id" element={<JobDetail />} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 매니저 방문 취소 (docs/manager-policy.md — 사용자 승인 2026-09-22) */
describe('Manager/Job/Detail 방문 취소', () => {
  beforeEach(() => {
    // 앞 테스트가 남긴 저장소를 비우고, 로그인한 매니저 세션(가입 완료)을 넣어 둔다
    sessionStorage.clear();
    localStorage.clear();
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: { id: 'm', role: 'manager', profileComplete: true } });
  });

  it('사유를 고르면 취소되고 홈으로 간다 — 사유 없이는 보낼 수 없다', async () => {
    // 준비: 가짜 서버의 기록(__fake)을 꺼내고, '방문 취소' 를 눌러 시트(role=dialog)를 연다. within(sheet) 는 시트 안에서만 찾는다
    const { __fake } = await import('../../api/http.js');
    renderDetail();
    fireEvent.click(await screen.findByRole('button', { name: '방문 취소' }));

    const sheet = screen.getByRole('dialog');
    const confirm = within(sheet).getByRole('button', { name: '방문 취소하기' });
    expect(confirm).toBeDisabled(); // 사유를 고르기 전

    fireEvent.click(await within(sheet).findByRole('radio', { name: '질병·부상' }));
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);

    // 확인: 홈으로 이동했고, 가짜 서버에 취소 요청 1건이 사유 키('sick' = 가짜 서버의 '질병·부상')로 남았다
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(__fake.cancelledJobs).toHaveLength(1);
    expect(__fake.cancelledJobs[0].reason).toBe('sick');
    expect(__fake.calls.find((c) => c.method === 'POST' && c.path.endsWith('/cancel')).idempotencyKey).toBeTruthy(); // 재시도가 두 번 처리되지 않게 (자체 점검 #44)
  });

  // 가짜 서버는 UNSTARTABLE_JOB_ID 의 취소를 409 INVALID_STATE + '… 고객센터로 전화해주세요' 로 거부한다
  // → 사유를 골라 보내면 그 서버 문구가 시트 안 role=alert(ActionError)로 보여야 한다
  it('방문 4시간 안이면 서버가 막고 그 문구를 보여준다', async () => {
    const { UNSTARTABLE_JOB_ID } = await import('../../test/fakeHttp.js');
    renderDetail(UNSTARTABLE_JOB_ID);
    fireEvent.click(await screen.findByRole('button', { name: '방문 취소' }));
    const sheet = screen.getByRole('dialog');
    fireEvent.click(await within(sheet).findByRole('radio', { name: '질병·부상' }));
    fireEvent.click(within(sheet).getByRole('button', { name: '방문 취소하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('고객센터로 전화해주세요');
  });

  // 방문일 전 작업(today: false, scope: [])을 연다 → 날짜가 붙은 시작 제목, '현장 도착' 대신 안내 문구,
  // '방문 취소' 버튼은 그대로, '청소 범위' 제목은 없음을 확인한다
  it('방문일 전에는 현장 도착 대신 안내, 옵션이 없으면 청소 범위를 그리지 않는다 (직접 사용 점검 U10·U11)', async () => {
    const { UNSTARTABLE_JOB_ID } = await import('../../test/fakeHttp.js');
    renderDetail(UNSTARTABLE_JOB_ID);
    expect(await screen.findByRole('heading', { name: '9월 16일 (수) 10:00 시작' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '현장 도착' })).not.toBeInTheDocument();
    expect(screen.getByText('현장 도착은 방문 당일에 할 수 있어요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '방문 취소' })).toBeInTheDocument(); // 취소는 그대로
    expect(screen.queryByRole('heading', { name: '청소 범위' })).not.toBeInTheDocument();
  });

  // 서버 ManagerCancelRequest.detail 은 @Size(max = 500) 이다. 입력란이 그보다 많이 받으면 501자부터는 확인을 눌러도
  // 서버가 400(VALIDATION)으로 거절해 시트가 닫히지 않았다 → 입력란의 maxlength 가 서버와 같은 500 인지 본다
  it('상세 입력란은 서버 상한(500자)까지만 받는다 (문제 목록 1번)', async () => {
    renderDetail();
    fireEvent.click(await screen.findByRole('button', { name: '방문 취소' }));
    const sheet = screen.getByRole('dialog');
    expect(within(sheet).getByRole('textbox', { name: '상세 내용 (선택)' })).toHaveAttribute('maxlength', '500');
  });

  // 비교용: 오늘 작업('job-1', 가짜 서버가 mock.js JOB_DETAIL 을 준다)은 '현장 도착' 버튼과 청소 범위가 있다
  it('당일 작업은 현장 도착이 보인다', async () => {
    renderDetail();
    expect(await screen.findByRole('button', { name: '현장 도착' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '청소 범위' })).toBeInTheDocument();
  });
});
