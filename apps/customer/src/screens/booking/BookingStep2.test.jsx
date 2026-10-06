// 예약 플로우 3단계 '기존 주소를 사용할까요?' 화면(BookingStep2.jsx) 테스트.
// '옵션 선택' 칸을 눌렀을 때 주소 선택에 따라 옵션 화면 / 주소 검색 화면으로 갈리는지 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingStep2 from './BookingStep2.jsx';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 기본 주소 조회도 거기서 답한다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 가정 청소(home)의 Step2 화면을 띄운다. 이동할 두 화면은 문구 한 줄(<p>)짜리 자리표시로 대신해 도착 여부만 본다
function renderStep2() {
  return render(
    <MemoryRouter initialEntries={['/booking/home/step2']}>
      <Routes>
        <Route path="/booking/:category/step2" element={<BookingStep2 />} />
        <Route path="/booking/:category/options" element={<p>옵션 화면</p>} />
        <Route path="/booking/:category/address-search" element={<p>주소 검색 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Booking/Step2 주소 선택', () => {
  // 예약 초안(sessionStorage)이 앞 테스트에서 남지 않게 비운다 — 둘째 테스트는 기본값 '네' 에 기대기 때문
  beforeEach(() => sessionStorage.clear());

  // 새 주소를 아직 넣지 않은 채 옵션 칸을 누르면, 기존 주소로 예약되지 않게 주소 검색부터 가야 한다
  it('"아니오" 를 고른 뒤 옵션 필드를 눌러도 주소 입력을 건너뛰지 않는다', async () => {
    renderStep2();
    fireEvent.click(await screen.findByRole('radio', { name: '아니오' }));
    fireEvent.click(screen.getByRole('button', { name: /옵션을 선택해주세요/ }));
    expect(await screen.findByText('주소 검색 화면')).toBeInTheDocument();
  });

  it('"네"(기본) 면 옵션 필드는 옵션 화면으로 간다', async () => {
    renderStep2();
    fireEvent.click(await screen.findByRole('button', { name: /옵션을 선택해주세요/ }));
    expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
  });
});
