// 청소 진행 화면(JobInProgress)의 흐름을 검증한다: 체크리스트 저장, 일시정지 순서(영상 차단 → 작업 상태),
// 일시정지 중간 실패 뒤 시트를 닫을 때의 복구, 앱 재진입 시 라이브 세션 다시 붙이기, 완료 뒤 start 재전송 없음.
// 라이브는 진짜 liveClient 를 쓰고, 그 아래 서버 호출은 test/fakeHttp.js 의 가짜 서버가 받는다.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobInProgress from './JobInProgress.jsx';
import { liveClient } from '../../live/liveClient.js';
import { ACTIVE_JOB } from '../../api/mock.js';

// api/http.js 를 가짜 서버로 바꾼다. 가짜 서버 상태(__fake)와 liveClient 상태는 이 파일의 테스트끼리 이어진다
// (그래서 몇몇 테스트는 끝에서 가짜 서버 값을 되돌리거나 liveClient.reset() 을 부른다)
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Manager/Job/InProgress 흐름', () => {
  // 라이브 세션은 테스트마다 새로 연다 — 앞 테스트의 일시정지(서버의 '멈춘 채' 표시)가 다음 테스트 화면에 시트를 띄우지 않게
  beforeEach(async () => {
    const { __fake } = await import('../../api/http.js');
    liveClient.reset();
    __fake.live = null;
    __fake.photoPhase = 'live';
    __fake.liveStopFails = false;
  });
  // 앞 테스트가 끝내거나 일시정지한 작업 대신, 진행 중 작업을 새로 둔다 (아래 두 테스트가 쓴다)
  const freshActiveJob = (fake) => {
    fake.activeJob = { ...ACTIVE_JOB, startedAt: '2026-09-15T05:02:00Z', pausedSeconds: 0, checklist: ACTIVE_JOB.checklist.map((g) => ({ ...g, items: g.items.map((i) => ({ ...i })) })) };
  };

  it('녹화 시작 실패를 송출 상태와 별도로 안내하고 종료 후 후 사진으로 진행한다', async () => {
    const { __fake } = await import('../../api/http.js');
    freshActiveJob(__fake);
    await liveClient.start('job-1');
    __fake.live = { ...__fake.live, recordingStatus: 'failed', recordingStartFailed: true };
    await liveClient.refresh('job-1');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/photos/after" element={<p>후 사진 촬영 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText(/녹화를 시작하지 못해 다시 시도하고 있어요/)).toBeInTheDocument();
    expect(liveClient.getState().status).toBe('live');
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 종료' }));
    expect(await screen.findByText('후 사진 촬영 화면')).toBeInTheDocument();
    expect(__fake.live.status).toBe('ended');
    liveClient.reset();
  });

  it('녹화 장애 중 원격 종료가 실패하면 후 사진으로 가지 않고 재시도한다', async () => {
    const { __fake } = await import('../../api/http.js');
    freshActiveJob(__fake);
    await liveClient.start('job-1');
    __fake.live = { ...__fake.live, recordingStatus: 'failed', recordingStartFailed: true };
    await liveClient.refresh('job-1');
    __fake.liveStopFails = true;
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/photos/after" element={<p>후 사진 촬영 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '실시간 영상 종료' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('실시간 영상을 종료하지 못했어요');
    expect(screen.queryByText('후 사진 촬영 화면')).not.toBeInTheDocument();
    expect(__fake.live.status).toBe('live');
    __fake.liveStopFails = false;
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 종료' }));
    expect(await screen.findByText('후 사진 촬영 화면')).toBeInTheDocument();
    expect(__fake.live.status).toBe('ended');
    liveClient.reset();
  });

  it('체크리스트를 켜면 진행률이 오르고, 일시정지 시트에서 사유를 골라 제출하면 라이브가 paused 로 바뀌며 /job/paused 로 간다', async () => {
    // 준비: 라이브 세션을 먼저 연다(가짜 서버는 바로 live). MemoryRouter 는 주소창 없이 경로를 흉내 내는 테스트용 라우터다.
    // 확인 1: 안 된 항목 하나를 누르면 진행 막대의 aria-valuenow(완료 수)가 오른다 — 저장 요청이 끝난 뒤라 waitFor 로 기다린다
    await liveClient.start('job-1');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/paused" element={<p>일시정지 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const boxes = await screen.findAllByRole('checkbox');
    const progress = screen.getByRole('progressbar');
    const before = Number(progress.getAttribute('aria-valuenow'));
    const unchecked = boxes.find((b) => !b.checked);
    fireEvent.click(unchecked);
    await waitFor(() => expect(Number(progress.getAttribute('aria-valuenow'))).toBeGreaterThan(before));

    // 확인 2: 시트를 열면 첫 사유('고객 요청')가 기본 선택, 다른 사유를 골라 제출하면 일시정지 화면으로 가고 라이브도 paused
    fireEvent.click(screen.getByRole('button', { name: '촬영 일시정지' }));
    const dialog = screen.getByRole('dialog');
    expect(await within(dialog).findByRole('radio', { name: '고객 요청' })).toHaveAttribute('aria-checked', 'true'); // 사유는 시트를 열 때 받는다
    fireEvent.click(within(dialog).getByRole('radio', { name: '카메라 문제' }));
    fireEvent.click(within(dialog).getByRole('button', { name: '촬영 일시정지' }));
    expect(await screen.findByText('일시정지 화면')).toBeInTheDocument();
    expect(liveClient.getState().status).toBe('paused');
  });

  it('영상만 막히고 작업 일시정지가 실패한 채 시트를 닫으면 영상을 다시 켠다 (자체 점검 #15 — 서버는 이 상태에서 스스로 켜지 않는다)', async () => {
    const { __fake } = await import('../../api/http.js');
    // 준비: 가짜 서버가 작업 일시정지(②)만 NETWORK 로 실패시킨다(jobPauseFails). vi.spyOn 은 진짜 resume 을 그대로 부르되 호출을 기록한다.
    // 확인: 실패 직후엔 영상을 켜지 않고 문구만, ESC 로 시트를 닫은 뒤에 resume('job-1') 이 불린다.
    // 서버는 이 상태를 매니저가 재개할 때까지 막아 둔다(바디캠이 다시 붙어도, video_held) — 그래서 닫기가 곧 재개다
    await liveClient.start('job-1');
    __fake.jobPauseFails = true;
    const resume = vi.spyOn(liveClient, 'resume');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/paused" element={<p>일시정지 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '촬영 일시정지' }));
    const dialog = screen.getByRole('dialog');
    await within(dialog).findByRole('radio', { name: '고객 요청' });
    fireEvent.click(within(dialog).getByRole('button', { name: '촬영 일시정지' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('이 창을 닫으면 촬영을 다시 시작해요');
    expect(resume).not.toHaveBeenCalled();
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(resume).toHaveBeenCalledWith('job-1'));
    __fake.jobPauseFails = false;
    resume.mockRestore();
  });

  it('작업 일시정지가 서버에선 됐는데 응답만 잃은 채 시트를 닫으면 상태를 다시 받아 일시정지 화면으로 간다 (자체 점검 #43)', async () => {
    const { __fake } = await import('../../api/http.js');
    // 준비: 가짜 서버가 작업을 paused 로 바꾸고도 NETWORK 오류를 돌려준다(jobPauseLost = 응답만 잃은 상황).
    // 확인: 시트를 닫으면 화면이 작업 상태를 다시 받아(refresh) paused 를 보고 일시정지 화면으로 간다. 끝에서 가짜 서버를 되돌린다
    await liveClient.start('job-1');
    __fake.jobPauseLost = true;
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/paused" element={<p>일시정지 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '촬영 일시정지' }));
    const dialog = screen.getByRole('dialog');
    await within(dialog).findByRole('radio', { name: '고객 요청' });
    fireEvent.click(within(dialog).getByRole('button', { name: '촬영 일시정지' }));
    await within(dialog).findByRole('alert');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(await screen.findByText('일시정지 화면')).toBeInTheDocument();
    __fake.jobPauseLost = false;
    __fake.activeJob.status = 'inProgress';
  });

  it('앱을 다시 열어 라이브 세션이 비어 있으면 서버의 작업 id 로 세션을 다시 붙인다 (일시정지가 null 로 가지 않게)', async () => {
    liveClient.reset(); // 앱 재실행 직후처럼 비운다 (idle)
    expect(liveClient.getState().status).toBe('idle');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
        </Routes>
      </MemoryRouter>,
    );
    // 화면이 뜬 뒤 효과가 서버의 작업 id('job-1')로 liveClient.start 를 불렀는지 본다 (연결 중·라이브·일시정지 중 하나면 붙은 것)
    await screen.findAllByRole('checkbox');
    await waitFor(() => expect(liveClient.getState().jobId).toBe('job-1'));
    await waitFor(() => expect(['connecting', 'live', 'paused']).toContain(liveClient.getState().status));
  });

  // 완료하면 liveClient 가 ended 가 되고 jobId 는 남는다 → 재진입 효과의 조건(다른 작업이거나 idle)에 걸리지 않아야 한다.
  // 가짜 서버의 호출 기록에서 /live/sessions/job-1/start 가 0건인지 센다
  it('청소 완료 뒤에는 끝난 세션에 start 를 다시 보내지 않는다', async () => {
    const { __fake } = await import('../../api/http.js');
    liveClient.reset();
    await liveClient.start('job-1');
    __fake.calls.length = 0; // 이 시점 이후의 호출만 센다
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
          <Route path="/job/photos/after" element={<p>후 사진 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    await screen.findAllByRole('checkbox');
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 종료' }));
    await waitFor(() => expect(screen.getByText('후 사진 화면')).toBeInTheDocument());
    const starts = __fake.calls.filter((c) => c.path === '/live/sessions/job-1/start');
    expect(liveClient.getState().status).toBe('ended');
    expect(starts.length).toBe(0); // 완료 뒤 start 를 다시 보내지 않는다
    liveClient.reset();
  });

  it('서버가 매니저가 멈춘 채라고 하면(작업은 진행 중) 시트를 다시 띄우고, 닫으면 촬영을 다시 시작한다', async () => {
    // ② 가 실패한 뒤 닫을 때의 재개까지 실패했거나 앱이 꺼졌다 다시 열린 경우 — 이 화면에는 재개 버튼이 없어 그대로 두면 끝까지 영상이 막힌다
    const { __fake } = await import('../../api/http.js');
    freshActiveJob(__fake);
    await liveClient.start('job-1');
    __fake.live = { ...__fake.live, status: 'paused', held: true };
    await liveClient.refresh('job-1'); // 3초 폴링 한 번 — 서버가 held 를 알려 준다
    const resume = vi.spyOn(liveClient, 'resume');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
        </Routes>
      </MemoryRouter>,
    );
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('영상은 중단됐지만');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(resume).toHaveBeenCalledWith('job-1'));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); // 재개가 닿아 서버도 멈춤을 지웠다
    resume.mockRestore();
    liveClient.reset();
  });

  it('영상 차단(①)이 확인 실패로 끝난 뒤 시트를 닫아도 재개를 보내 서버와 맞춘다', async () => {
    // 이전 시도에서 ① 이 '멈췄다' 였거나 응답을 잃었을 수 있다 — 서버가 멈춘 채로 남지 않게 닫을 때 재개를 보낸다 (이미 나가고 있으면 아무 일 없다)
    const { __fake } = await import('../../api/http.js');
    freshActiveJob(__fake);
    await liveClient.start('job-1');
    __fake.livePauseFails = true;
    const resume = vi.spyOn(liveClient, 'resume');
    render(
      <MemoryRouter initialEntries={['/job/in-progress']}>
        <Routes>
          <Route path="/job/in-progress" element={<JobInProgress />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '촬영 일시정지' }));
    const dialog = screen.getByRole('dialog');
    await within(dialog).findByRole('radio', { name: '고객 요청' });
    fireEvent.click(within(dialog).getByRole('button', { name: '촬영 일시정지' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('영상이 계속 전송되고 있으니');
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(resume).toHaveBeenCalledWith('job-1'));
    __fake.livePauseFails = false;
    resume.mockRestore();
    liveClient.reset();
  });
});

