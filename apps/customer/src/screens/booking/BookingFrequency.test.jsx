// 서비스 주기 화면(BookingFrequency) 테스트: 두 조회 중 하나(주기 설명 문구)만 실패했을 때
// 오류 화면의 '다시 시도' 가 실패한 쪽을 다시 불러 화면이 뜨는지, 정기청소 회차 안내가 서버 회차 수를 쓰는지 본다. 서버는 가짜(test/fakeHttp.js)다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingFrequency from './BookingFrequency.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/Frequency', () => {
  beforeEach(() => sessionStorage.clear());

  it('확인 문구 조회만 실패해도 다시 시도하면 화면이 뜬다 (자체 점검 #35)', async () => {
    const { __fake } = await import('../../api/http.js');
    // failOnce: 가짜 서버가 이 요청만 딱 한 번 네트워크 오류로 실패시킨다 — 다시 시도하면 성공한다
    __fake.failOnce.add('GET /catalog/categories/home/confirm-copy');
    render(
      <MemoryRouter initialEntries={['/booking/home/frequency']}>
        <Routes>
          <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { name: '서비스 주기' })).toBeInTheDocument();
  });

  // 운영자가 정기청소 회차 수를 3으로 바꿨다(서버 confirm-copy 의 seriesTermMonths = 3) →
  // 주기 안내가 앱 고정 '6개월 동안 6회' 가 아니라 '3개월 동안 3회' 를 말한다 (문제 목록 25번)
  it('정기청소 회차 안내는 서버가 내려 준 회차 수를 쓴다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.seriesTermMonths = 3;
    render(
      <MemoryRouter initialEntries={['/booking/home/frequency']}>
        <Routes>
          <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText(/3개월 동안 3회 방문해요/)).toBeInTheDocument();
    expect(screen.queryByText(/6개월 동안 6회/)).toBeNull();
    __fake.seriesTermMonths = 6; // 이 파일은 가짜 서버 상태를 이어 쓰므로 되돌린다
  });
});
