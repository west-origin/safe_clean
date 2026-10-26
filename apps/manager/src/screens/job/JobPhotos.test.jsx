// 필수 전 사진 → 라이브 → 종료 → 필수 후 사진 → 완료 흐름.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import JobInProgress from './JobInProgress.jsx';
import JobBeforePhotos from './JobBeforePhotos.jsx';
import JobPhotoCapture from './JobPhotoCapture.jsx';
import JobAfterPhotos from './JobAfterPhotos.jsx';
import { liveClient } from '../../live/liveClient.js';
import { ACTIVE_JOB } from '../../api/mock.js';
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

function renderAt(path) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/job/in-progress" element={<JobInProgress />} />
    <Route path="/job/photos/before" element={<JobBeforePhotos />} />
    <Route path="/job/photo" element={<JobPhotoCapture />} />
    <Route path="/job/photos/after" element={<JobAfterPhotos />} />
    <Route path="/job/complete" element={<p>완료 화면</p>} />
  </Routes></MemoryRouter>);
}
const photo = (name, after = false) => ({ id: name === '안방' ? 'bedroom' : name, location: name, beforeUrl: '/before.jpg', afterUrl: after ? '/after.jpg' : null });
describe('공간별 필수 사진', () => {
  let fake;
  beforeEach(async () => {
    ({ __fake: fake } = await import('../../api/http.js'));
    liveClient.reset(); fake.live = null; fake.calls.length = 0;
    fake.photoPhase = 'before'; fake.requiredRooms = ['거실', '안방']; fake.jobPhotos = [];
    fake.activeJob = structuredClone(ACTIVE_JOB);
    URL.createObjectURL = vi.fn(() => 'blob:shot'); URL.revokeObjectURL = vi.fn();
    vi.spyOn(liveClient, 'preparePhoto').mockResolvedValue();
    vi.spyOn(liveClient, 'capturePhoto').mockResolvedValue(new File(['x'], 'p.jpg', { type: 'image/jpeg' }));
  });
  afterEach(() => { liveClient.reset(); vi.restoreAllMocks(); });

  it('진행 화면 직접 진입도 전 사진으로 돌리고 송출하지 않는다', async () => {
    renderAt('/job/in-progress');
    expect(await screen.findByRole('button', { name: '실시간 영상 시작' })).toBeDisabled();
    expect(fake.calls.some((c) => c.path.endsWith('/start'))).toBe(false);
  });
  it('한 공간이라도 전 사진이 빠지면 송출 시작 불가', async () => {
    fake.jobPhotos = [photo('거실')]; renderAt('/job/photos/before');
    expect(await screen.findByRole('button', { name: '실시간 영상 시작' })).toBeDisabled();
    expect(screen.getByRole('status')).toHaveTextContent('1곳');
  });
  it('등록한 공간 촬영 후 저장하며 라이브 시작 API는 부르지 않는다', async () => {
    renderAt('/job/photo?room=안방');
    fireEvent.click(await screen.findByRole('button', { name: '촬영하기' }));
    fireEvent.click(await screen.findByRole('button', { name: '저장' }));
    expect(await screen.findByRole('button', { name: '실시간 영상 시작' })).toBeDisabled();
    expect(fake.jobPhotos[0].location).toBe('안방');
    expect(fake.calls.some((c) => c.path.includes('/live/') && c.path.endsWith('/start'))).toBe(false);
  });
  it('모든 전 사진이 있어야 서버 확인 후 진행 화면으로 간다', async () => {
    fake.jobPhotos = fake.requiredRooms.map((r) => photo(r)); renderAt('/job/photos/before');
    fireEvent.click(await screen.findByRole('button', { name: '실시간 영상 시작' }));
    expect(await screen.findByRole('button', { name: '실시간 영상 종료' })).toBeInTheDocument();
    const paths = fake.calls.map((c) => c.path);
    expect(paths.findIndex((p) => p.endsWith('/confirm-before'))).toBeLessThan(paths.findIndex((p) => p.includes('/live/') && p.endsWith('/start')));
  });
  it('현장 공간을 추가하면 필수 촬영 목록에도 추가된다', async () => {
    renderAt('/job/photos/before');
    fireEvent.change(await screen.findByLabelText('추가 공간 이름'), { target: { value: '작은방' } });
    fireEvent.click(screen.getByRole('button', { name: '공간 추가' }));
    expect(await screen.findByText('작은방')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('3곳');
  });
  it('종료 의도 저장과 라이브 종료가 후 사진 화면보다 먼저다', async () => {
    fake.photoPhase = 'live'; fake.jobPhotos = fake.requiredRooms.map((r) => photo(r)); renderAt('/job/in-progress');
    fireEvent.click(await screen.findByRole('button', { name: '실시간 영상 종료' }));
    expect(await screen.findByRole('button', { name: '청소 완료' })).toBeDisabled();
    const paths = fake.calls.map((c) => c.path);
    expect(paths.findIndex((p) => p.endsWith('/finish-video'))).toBeLessThan(paths.findIndex((p) => p.includes('/live/') && p.endsWith('/stop')));
  });
  it('후 촬영에는 전 사진이 겹치며 송출을 다시 시작하지 않는다', async () => {
    fake.photoPhase = 'after'; fake.jobPhotos = [photo('안방')]; renderAt('/job/photo?id=bedroom');
    expect(await screen.findByAltText('안방 청소 전')).toHaveAttribute('src', '/before.jpg');
    fireEvent.load(screen.getByAltText('안방 청소 전'));
    fireEvent.click(screen.getByRole('button', { name: '촬영하기' }));
    fireEvent.click(await screen.findByRole('button', { name: '저장' }));
    expect(await screen.findByRole('button', { name: '청소 완료' })).toBeDisabled();
    expect(fake.calls.some((c) => c.path.includes('/live/') && c.path.endsWith('/start'))).toBe(false);
  });
  it('전 사진 로딩을 확인하고 원본 보기와 카메라 겹쳐 보기를 전환한다', async () => {
    fake.photoPhase = 'after'; fake.jobPhotos = [photo('안방')]; renderAt('/job/photo?id=bedroom');
    const reference = await screen.findByAltText('안방 청소 전');
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeDisabled();
    fireEvent.load(reference);
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeEnabled();
    fireEvent.click(screen.getByRole('button', { name: '청소 전 사진만 보기' }));
    expect(screen.getByRole('button', { name: '카메라와 겹쳐 보기' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '카메라와 겹쳐 보기' }));
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeEnabled();
  });
  it('전 사진을 못 불러오면 촬영을 막고 새 URL로 다시 불러온다', async () => {
    fake.photoPhase = 'after'; fake.jobPhotos = [photo('안방')]; renderAt('/job/photo?id=bedroom');
    fireEvent.error(await screen.findByAltText('안방 청소 전'));
    expect(screen.getByRole('alert')).toHaveTextContent('청소 전 사진을 불러오지 못했어요');
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeDisabled();
    fake.jobPhotos[0].beforeUrl = '/renewed-before.jpg';
    fireEvent.click(screen.getByRole('button', { name: '전 사진 다시 불러오기' }));
    await waitFor(() => expect(screen.getByAltText('안방 청소 전')).toHaveAttribute('src', '/renewed-before.jpg'));
    fireEvent.load(screen.getByAltText('안방 청소 전'));
    expect(screen.getByRole('button', { name: '촬영하기' })).toBeEnabled();
  });
  it('모든 공간의 후 사진이 있어야 완료 확인과 완료 요청이 가능하다', async () => {
    fake.photoPhase = 'after'; fake.jobPhotos = fake.requiredRooms.map((r) => photo(r, true)); renderAt('/job/photos/after');
    fireEvent.click(await screen.findByRole('button', { name: '청소 완료' }));
    const dialog = await screen.findByRole('dialog', { name: '청소를 완료하시겠어요?' });
    fireEvent.click(within(dialog).getByRole('button', { name: '청소 완료' }));
    expect(await screen.findByText('완료 화면')).toBeInTheDocument();
  });
  it('종료에 실패하면 촬영/완료 화면을 열지 않고 재시도한다', async () => {
    fake.photoPhase = 'after'; fake.jobPhotos = fake.requiredRooms.map((r) => photo(r, true));
    vi.spyOn(liveClient, 'stop').mockRejectedValueOnce(new Error('영상 종료 실패')).mockResolvedValueOnce();
    renderAt('/job/photos/after');
    expect(await screen.findByText('영상 종료 실패')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '청소 완료' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '청소 완료' })).toBeEnabled());
  });
});
