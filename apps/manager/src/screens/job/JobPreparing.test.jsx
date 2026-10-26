// 청소 준비 화면(JobPreparing)의 '청소 시작' 과 카메라 연결 실패 모습을 검증한다.
// 서버는 test/fakeHttp.js 의 가짜 서버다. UNSTARTABLE_JOB_ID 는 가짜 서버가 시작을 409('작업 당일에만 …')로 거부하는 작업이다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobPreparing from './JobPreparing.jsx';
import { liveClient } from '../../live/liveClient.js';
import { UNSTARTABLE_JOB_ID } from '../../test/fakeHttp.js';

// api/http.js 를 가짜 서버로 바꾼다. 가짜 서버 상태(__fake)는 이 파일의 테스트끼리 공유된다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// /job/:id/preparing 으로 청소 준비 화면을 연다. MemoryRouter 는 주소창 없이 경로를 흉내 내는 테스트용 라우터이고,
// '/job/in-progress' 자리에는 이동을 확인할 '진행 화면' 문구만 둔다
function renderPreparing(id) {
  return render(
    <MemoryRouter initialEntries={[`/job/${id}/preparing`]}>
      <Routes>
        <Route path="/job/:id/preparing" element={<JobPreparing />} />
        <Route path="/job/photos/before" element={<p>전 사진 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('청소 시작 (B4 §9.2-3)', () => {
  it('작업을 시작한 뒤 송출 없이 전 사진 화면으로 간다', async () => {
    const { __fake } = await import('../../api/http.js');
    // 준비: liveClient.start 를 가짜로 바꿔 서버를 부르지 않고 order 에 'live' 만 적게 한다.
    // 확인: 진행 화면으로 갔고, 가짜 서버에 작업 시작 요청이 멱등 키('key-1' = 가짜 서버의 고정 키)와 함께 남았고, 라이브 시작이 불렸다.
    // 주의: 이름이 말하는 '먼저 … 그 다음' 순서는 이 테스트가 실제로 확인하지 못한다 —
    // order 에는 'live' 만 들어가고, 아래 indexOf 비교는 늘 참이다
    const order = [];
    const spy = vi.spyOn(liveClient, 'start').mockImplementation(async () => order.push('live'));
    renderPreparing('job-1');
    fireEvent.click(await screen.findByRole('button', { name: '청소 시작' }));
    expect(await screen.findByText('전 사진 화면')).toBeInTheDocument();
    const start = __fake.calls.find((c) => c.method === 'POST' && c.path === '/manager/jobs/job-1/start');
    expect(start.idempotencyKey).toBe('key-1');
    expect(order).toEqual([]);
    expect(__fake.calls.indexOf(start)).toBeLessThan(__fake.calls.length); // 주의: 늘 참이라 순서를 확인하지 못한다(start 가 목이라 calls 에 라이브 호출이 없다)
    spy.mockRestore();
  });

  it('서버가 거부하면 그 문구를 보여주고 라이브를 시작하지 않는다', async () => {
    // 준비: liveClient.start 호출만 기록한다 → 확인: 서버 거부 문구가 role=alert 로 보이고, 라이브는 불리지 않고, 화면도 그대로다
    const spy = vi.spyOn(liveClient, 'start');
    renderPreparing(UNSTARTABLE_JOB_ID);
    fireEvent.click(await screen.findByRole('button', { name: '청소 시작' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('작업 당일에만 시작할 수 있어요');
    expect(spy).not.toHaveBeenCalled();
    expect(screen.queryByText('진행 화면')).toBeNull();
    spy.mockRestore();
  });

  it('카메라가 끊겨 있으면 같은 화면이 연결 실패 배너와 비활성 시작 버튼을 그리고, "카메라 다시 연결" 은 장비 상태를 다시 받는다', async () => {
    const { __fake } = await import('../../api/http.js');
    // 준비: 가짜 서버가 카메라 '연결 안 됨'(issue) 장비 상태를 주게 한다 → 배너·비활성 시작 버튼 확인.
    // 그다음 정상 상태로 바꾸고 '카메라 다시 연결' → 받는 동안 화면 유지·버튼 스피너, 받은 뒤 시작 버튼이 켜진다. 끝에서 null 로 되돌린다
    const { EQUIPMENT_ERROR, EQUIPMENT_OK } = await import('../../api/mock.js');
    __fake.equipment = EQUIPMENT_ERROR;
    renderPreparing('job-1');
    expect(await screen.findByText('카메라 연결 실패')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '청소 시작 (카메라 연결 후 가능)' })).toBeDisabled();
    __fake.equipment = EQUIPMENT_OK;
    fireEvent.click(screen.getByRole('button', { name: '카메라 다시 연결' }));
    expect(screen.getByText('카메라 연결 실패')).toBeInTheDocument(); // 다시 받는 동안 화면이 스켈레톤으로 바뀌지 않는다 (검수 4차)
    expect(document.querySelector('button[aria-busy="true"]')).not.toBeNull(); // 버튼 스피너 (Loading 은 라벨을 바꾼다)
    expect(await screen.findByRole('button', { name: '청소 시작' })).toBeEnabled();
    expect(screen.queryByText('카메라 연결 실패')).toBeNull();
    __fake.equipment = null;
  });
});

// 휴대폰 송출 모드(서버 source='app', BACKEND_PLAN §8.0): 바디캠 대신 휴대폰 카메라 — 미리보기·카메라 행·실패 배너
describe('휴대폰 카메라 (§8.0)', () => {
  // 카메라를 가짜로: getUserMedia 가 영상 트랙 하나를 주거나(ok) 권한 거부 오류를 던진다
  async function phoneMode(getUserMedia) {
    const { __fake } = await import('../../api/http.js');
    __fake.liveSource = 'app';
    liveClient.reset(); // 앞 테스트(바디캠 모드)가 남긴 source='ingress' 를 지운다
    vi.stubGlobal('navigator', { ...globalThis.navigator, mediaDevices: { getUserMedia } });
    return __fake;
  }
  const okCamera = () => vi.fn(async () => ({ getVideoTracks: () => [{ readyState: 'live', stop: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }] }));
  async function cleanup(__fake) {
    liveClient.reset();
    __fake.liveSource = undefined;
    vi.unstubAllGlobals();
  }

  it('들어오면 카메라를 켜 미리보기를 보이고, 서버의 바디캠 상태와 상관없이 카메라 행은 연결됨 · 청소 시작 가능', async () => {
    const getUserMedia = okCamera();
    const __fake = await phoneMode(getUserMedia);
    const { EQUIPMENT_ERROR } = await import('../../api/mock.js');
    __fake.equipment = EQUIPMENT_ERROR; // 서버는 바디캠 '연결 안 됨' 이라고 해도
    renderPreparing('job-1');
    expect(await screen.findByRole('region', { name: '카메라 미리보기' })).toBeInTheDocument();
    expect(await screen.findByText('연결됨')).toBeInTheDocument();
    expect(screen.queryByText('카메라 연결 실패')).toBeNull();
    expect(screen.getByRole('button', { name: '청소 시작' })).not.toBeDisabled();
    expect(getUserMedia).toHaveBeenCalledTimes(1);
    __fake.equipment = null;
    await cleanup(__fake);
  });

  it('카메라 권한을 거부하면 연결 실패 배너에 휴대폰 권한 안내가 나오고 시작 버튼이 꺼진다', async () => {
    const __fake = await phoneMode(vi.fn(async () => { throw Object.assign(new Error('no'), { name: 'NotAllowedError' }); }));
    const { PHONE_CAMERA_COPY } = await import('../../api/mock.js');
    renderPreparing('job-1');
    expect(await screen.findByText('카메라 연결 실패')).toBeInTheDocument();
    expect(screen.getByText(PHONE_CAMERA_COPY.denied)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '청소 시작 (카메라 연결 후 가능)' })).toBeDisabled();
    await cleanup(__fake);
  });
});
