import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import ServiceLivePortrait from './ServiceLivePortrait.jsx';
import ServiceLiveLandscape from './ServiceLiveLandscape.jsx';
import { liveClient } from '../../live/liveClient.js';
import { LIVE_CHECKLIST } from '../../api/mock.js';
import { LIVE_BOOKING_ID, READY_BOOKING_ID } from '../../test/fakeHttp.js';
import { __fake } from '../../api/http.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
const route = (path) => render(<MemoryRouter initialEntries={[path]}><Routes>
  <Route path="/service/live" element={<ServiceLivePortrait />} />
  <Route path="/service/live/landscape" element={<ServiceLiveLandscape />} />
</Routes></MemoryRouter>);
const advance = (ms = 0) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });

describe('고객 실시간 체크리스트와 다시보기', () => {
  beforeEach(() => { vi.useFakeTimers(); __fake.calls.length = 0; __fake.liveChecklists = {}; });
  afterEach(() => { liveClient.leave(); vi.useRealTimers(); });

  it('매니저 상태가 바뀌면 5초 폴링으로 카드가 갱신된다', async () => {
    route(`/service/live?booking=${LIVE_BOOKING_ID}`);
    await advance();
    expect(screen.getByText('9개 중 4개 완료')).toBeInTheDocument();
    const updated = structuredClone(LIVE_CHECKLIST);
    updated.done = 9; updated.percent = 100;
    updated.groups.forEach((group) => { group.done = group.total; group.items.forEach((item) => { item.done = true; }); });
    __fake.liveChecklists[LIVE_BOOKING_ID] = updated;
    await advance(5000);
    expect(screen.getByText('9개 중 9개 완료')).toBeInTheDocument();
    expect(screen.getByLabelText('가스레인지 완료')).toBeInTheDocument();
  });

  it('다시보기는 요청사항만 표시하고 메타데이터 뒤 구간이 생긴다', async () => {
    const { container } = route(`/service/live?booking=${READY_BOOKING_ID}&replay=1`);
    await advance();
    expect(screen.getByText('고객 요청사항')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: '청소 체크리스트' })).toBeNull();
    const video = container.querySelector('video');
    Object.defineProperty(video, 'duration', { value: 630, configurable: true });
    video.currentTime = 319;
    await act(async () => { video.dispatchEvent(new Event('timeupdate')); });
    expect(screen.getByRole('slider', { name: '재생 위치' })).toHaveAttribute('aria-valuetext', '5:19 / 10:30 · 주방 · 싱크대');
    const count = () => __fake.calls.filter((c) => c.path.endsWith('/checklist')).length;
    const before = count();
    await advance(10000);
    expect(count()).toBe(before);
  });

  it('체크리스트 초기 실패를 다시 조회해도 영상은 유지된다', async () => {
    __fake.failOnce.add(`GET /customer/bookings/${LIVE_BOOKING_ID}/checklist`);
    const { container } = route(`/service/live?booking=${LIVE_BOOKING_ID}`);
    await advance();
    expect(container.querySelector('video')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await advance();
    expect(screen.getByText('9개 중 4개 완료')).toBeInTheDocument();
  });

  it('실시간 가로 화면에서는 체크리스트 API를 부르지 않는다', async () => {
    route(`/service/live/landscape?booking=${LIVE_BOOKING_ID}`);
    await advance();
    expect(__fake.calls.some((c) => c.path.endsWith('/checklist'))).toBe(false);
  });
});
