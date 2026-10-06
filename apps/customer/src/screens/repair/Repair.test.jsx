// 고객 앱 수리 탭 화면(Repair.jsx) 테스트. 수리 카테고리 칸을 눌렀을 때 수리 예약 플로우의 어느 단계로 가는지 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Repair from './Repair.jsx';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — GET /customer/repair 의 히어로 문구가 여기서 온다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Repair 화면', () => {
  // 예약 초안이 저장되는 sessionStorage 를 테스트마다 비운다 — 이 화면은 지금 초안을 쓰지 않지만 다른 예약 화면 테스트와 같은 준비로 둔다
  beforeEach(() => sessionStorage.clear());

  // 준비: 이동할 화면들은 문구 한 줄(<p>)로 대신한다. 확인: '배관·누수' 칸을 누르면 증상 상세 화면에 도착한다
  it('가전수리는 가전 종류 화면으로, 다른 수리 종류는 증상 상세 화면으로 간다 (수리 예약 플로우)', async () => {
    render(
      <MemoryRouter initialEntries={['/repair']}>
        <Routes>
          <Route path="/repair" element={<Repair />} />
          <Route path="/repair/booking/:category/appliance" element={<p>가전 종류 화면</p>} />
          <Route path="/repair/booking/:category/detail" element={<p>증상 상세 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /배관·누수/ }));
    expect(await screen.findByText('증상 상세 화면')).toBeInTheDocument();
  });

  // '가전수리' 칸만 가전 종류 선택 화면(/repair/booking/appliance/appliance)으로 간다
  it('가전수리는 가전 종류 선택부터', async () => {
    render(
      <MemoryRouter initialEntries={['/repair']}>
        <Routes>
          <Route path="/repair" element={<Repair />} />
          <Route path="/repair/booking/:category/appliance" element={<p>가전 종류 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: /가전수리/ }));
    expect(await screen.findByText('가전 종류 화면')).toBeInTheDocument();
  });
});
