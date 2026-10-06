// 촬영 일시정지 화면(JobPaused)의 재개를 검증한다: ① 작업 재개 → ② 영상 재개 순서, ① 실패 시 화면에 남기,
// ① 만 되고 나갔다 온 경우(일시정지 조회 404)의 복구. 서버는 test/fakeHttp.js 의 가짜 서버다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobPaused from './JobPaused.jsx';
import { liveClient } from '../../live/liveClient.js';
import { resumeJob } from '../../api/manager.js';

// api/http.js 는 가짜 서버로 바꾸고, manager.js 는 진짜를 쓰되 resumeJob 만 vi.fn 으로 감싼다
// (진짜 함수를 그대로 부르면서 호출을 기록하고, 테스트가 한 번만 실패하게 바꿀 수 있다)
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
vi.mock('../../api/manager.js', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, resumeJob: vi.fn(actual.resumeJob) };
});

describe('Manager/Job/Paused', () => {
  it('다시 시작은 서버가 준 작업 id 로 작업 상태를 먼저 바꾸고, 영상 재개가 실패해도 작업은 이미 진행 중이라 진행 화면으로 간다 (자체 점검 #42)', async () => {
    // 앱을 다시 연 경우(라이브 세션 비어 있음)도 같은 경로 — 화면이 서버의 jobId 로 live.start 를 다시 붙인다
    // 준비: 라이브를 연 뒤 일시정지해 두고, liveClient.resume(②)이 이번 한 번만 실패하게 한다(mockRejectedValueOnce)
    await liveClient.start('job-1');
    await liveClient.pause();
    const resume = vi.spyOn(liveClient, 'resume').mockRejectedValueOnce(new Error('네트워크'));
    render(
      <MemoryRouter initialEntries={['/job/paused']}>
        <Routes>
          <Route path="/job/paused" element={<JobPaused />} />
          <Route path="/job/in-progress" element={<p>진행 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '촬영 다시 시작' }));
    await waitFor(() => expect(screen.getByText('진행 화면')).toBeInTheDocument()); // 서버의 unmute_pending 을 재조정기가 이어 받는다
    expect(resumeJob).toHaveBeenCalledWith('job-1');
    expect(resume).toHaveBeenCalledWith('job-1');
    expect(liveClient.getState().jobId).toBe('job-1');
    resume.mockRestore();
  });

  it('작업 재개(①)가 실패하면 일시정지 화면에 남아 다시 시도할 수 있다', async () => {
    // 준비: resumeJob(①)이 이번 한 번만 실패 → 확인: 실패 문구(role=alert)가 보이고 진행 화면으로 가지 않는다
    resumeJob.mockRejectedValueOnce(new Error('네트워크'));
    render(
      <MemoryRouter initialEntries={['/job/paused']}>
        <Routes>
          <Route path="/job/paused" element={<JobPaused />} />
          <Route path="/job/in-progress" element={<p>진행 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '촬영 다시 시작' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('다시 시작하지 못했어요');
    expect(screen.queryByText('진행 화면')).toBeNull();
  });

  it('작업 재개만 되고 영상 재개 전에 나갔다 오면(일시정지 조회 404) 영상 재개를 다시 부르고 진행 화면으로 간다 (재감사 2차 #5)', async () => {
    // 준비: 가짜 서버의 일시정지 조회를 404 로(pausedJob = null), liveClient 는 비우고 start·resume 은 바로 성공하는 가짜로 바꾼다.
    // 확인: 화면이 스스로 진행 화면으로 가고, start 가 resume 보다 먼저 불렸다(invocationCallOrder = 불린 순서 번호)
    const { __fake } = await import('../../api/http.js');
    __fake.pausedJob = null;
    liveClient.reset(); // 앱을 다시 연 직후처럼 세션이 비어 있다 (자체 점검 #41)
    const start = vi.spyOn(liveClient, 'start').mockResolvedValue(undefined);
    const resume = vi.spyOn(liveClient, 'resume').mockResolvedValue(undefined);
    render(
      <MemoryRouter initialEntries={['/job/paused']}>
        <Routes>
          <Route path="/job/paused" element={<JobPaused />} />
          <Route path="/job/in-progress" element={<p>진행 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('진행 화면')).toBeInTheDocument();
    expect(start).toHaveBeenCalledWith('job-1'); // 영상 재개 전에 세션부터 다시 붙인다 — 안 그러면 상태만 live 이고 폴링·프리뷰가 없는 채로 남는다
    expect(start.mock.invocationCallOrder[0]).toBeLessThan(resume.mock.invocationCallOrder[0]);
    expect(resume).toHaveBeenCalledWith('job-1');
    start.mockRestore();
    resume.mockRestore();
  });
});
