// 고객 시청 화면의 청소 전·후 비교 (2026-10-23, 코드에서 정함): 매니저가 완료하기 전에는 보이지 않고, 완료(ready)되면 위치별 비교 슬라이더가 나타난다.
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import ServiceLivePortrait from './ServiceLivePortrait.jsx';
import { liveClient } from '../../live/liveClient.js';
import { CLEANING_PHOTOS } from '../../api/mock.js';
import { LIVE_BOOKING_ID, READY_BOOKING_ID } from '../../test/fakeHttp.js';
import { __fake } from '../../api/http.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
const route = (path) => render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/service/live" element={<ServiceLivePortrait />} /></Routes></MemoryRouter>);
const advance = (ms = 0) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });
const photoCalls = (id) => __fake.calls.filter((c) => c.path === `/customer/bookings/${id}/photos`).length;

describe('고객 청소 전·후 비교', () => {
  beforeEach(() => { vi.useFakeTimers(); __fake.calls.length = 0; __fake.cleaningPhotos = {}; });
  afterEach(() => { liveClient.leave(); vi.useRealTimers(); });

  it('녹화본(청소 완료)에는 위치마다 비교 슬라이더가 보인다', async () => {
    route(`/service/live?booking=${READY_BOOKING_ID}&replay=1`);
    await advance();
    expect(screen.getByRole('heading', { name: '청소 전·후 비교' })).toBeInTheDocument();
    expect(screen.getByRole('slider', { name: '거실 소파 앞 청소 전후 비교' })).toBeInTheDocument();
    expect(screen.getByAltText('주방 싱크대 청소 후')).toHaveAttribute('src', CLEANING_PHOTOS.photos[1].afterUrl);
  });

  it('실시간 중에는 보이지 않다가 매니저가 완료하면 10초 안에 나타나고, 그 뒤로는 다시 묻지 않는다', async () => {
    route(`/service/live?booking=${LIVE_BOOKING_ID}`);
    await advance();
    expect(screen.queryByRole('heading', { name: '청소 전·후 비교' })).toBeNull();
    __fake.cleaningPhotos[LIVE_BOOKING_ID] = CLEANING_PHOTOS;
    await advance(10_000);
    expect(screen.getByRole('heading', { name: '청소 전·후 비교' })).toBeInTheDocument();
    const calls = photoCalls(LIVE_BOOKING_ID);
    await advance(30_000);
    expect(photoCalls(LIVE_BOOKING_ID)).toBe(calls);
  });
});
