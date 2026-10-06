import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import CallDetail from './CallDetail.jsx';

// 매니저 콜 상세 화면의 만료 카운트다운 테스트: 남은 시간이 1초마다 줄고, 0 이 되면 마감 안내로 바뀌는지 본다.

// http.js 를 가짜 서버(test/fakeHttp.js)로 바꿔 끼운다.
// 화면은 실제 manager.js 를 거쳐 가짜 서버를 부른다. 가짜 서버 상태는 import 한 모듈의 __fake 로 들여다본다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Manager/Call/Detail 만료 시간', () => {
  afterEach(() => vi.useRealTimers()); // 테스트가 실패해도 다음 테스트가 가짜 시계에 묶이지 않게 되돌린다

  it('만료까지 남은 시간이 1초마다 줄고, 지나면 마감 안내로 바뀐다 (자체 점검 #46)', async () => {
    // 가짜 시계: setInterval·Date.now 를 테스트가 앞당길 수 있다. shouldAdvanceTime 이면 실제 시간만큼도 흘러 조회 응답은 평소처럼 온다
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { __fake } = await import('../../api/http.js');
    __fake.offers[0].expiresAt = new Date(Date.now() + 65_000).toISOString(); // 첫 콜(call-1)을 65초 뒤 만료로 바꾼다
    // MemoryRouter: 주소창 없이 메모리 안에서 라우팅한다. /call/:id 라우트로 그려야 화면의 useParams 가 id 를 읽는다
    render(
      <MemoryRouter initialEntries={['/call/call-1']}>
        <Routes>
          <Route path="/call/:id" element={<CallDetail />} />
        </Routes>
      </MemoryRouter>,
    );
    // act: 시계를 돌린 뒤의 다시 그리기를 끝까지 반영한 다음에 확인한다
    expect(await screen.findByText(/1:0\d 후 만료/)).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(10_000); }); // 10초 뒤 → 55초 안팎 남음
    expect(screen.getByText(/0:5\d 후 만료/)).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(60_000); }); // 모두 70초 → 만료가 지났다
    expect(screen.getByText('이미 마감된 요청이에요')).toBeInTheDocument();
  });
});
