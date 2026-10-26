// 예약 플로우 4단계 주소 검색 화면(BookingAddressSearch.jsx) 테스트.
// 검색어를 적으면 서버 검색 결과(가짜 서버는 mock ADDRESS_RESULTS)가 목록으로 뜨고, 고른 주소로 주소 확인 화면에 가는지 본다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingAddressSearch from './BookingAddressSearch.jsx';
import { ADDRESS_RESULTS, ADDRESS_SEARCH_COPY } from '../../api/mock.js';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — GET /catalog/address 는 검색어와 상관없이 ADDRESS_RESULTS
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

function renderSearch() {
  return render(
    <MemoryRouter initialEntries={['/booking/home/address-search']}>
      <Routes>
        <Route path="/booking/:category/address-search" element={<BookingAddressSearch />} />
        <Route path="/booking/:category/address-confirm" element={<p>주소 확인 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Booking/AddressSearch 주소 검색', () => {
  beforeEach(() => sessionStorage.clear());

  it('검색어가 없으면 입력 안내를 보인다', () => {
    renderSearch();
    expect(screen.getByText(ADDRESS_SEARCH_COPY.guide)).toBeInTheDocument();
  });

  it('검색어를 적으면 검색 결과가 뜨고, 고른 도로명주소로 주소 확인 화면에 간다', async () => {
    renderSearch();
    fireEvent.change(screen.getByRole('searchbox', { name: '주소 검색' }), { target: { value: '테헤란로' } });
    const [first] = await screen.findAllByRole('button', { name: new RegExp(ADDRESS_RESULTS[0].main) }); // 123-1 · 123길 … 도 같은 글자로 시작한다
    fireEvent.click(first);
    expect(await screen.findByText('주소 확인 화면')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home')).roadAddress).toBe(ADDRESS_RESULTS[0].main); // 예약 초안에 들어갔다
  });
});
