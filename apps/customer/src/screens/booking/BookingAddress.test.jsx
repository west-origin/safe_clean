// 주소 선택 화면(BookingAddress) 테스트: 저장 주소가 있으면 목록(기본 주소 선택됨)을 보이고 "다음" 이 날짜 화면으로,
// 저장 주소가 없으면 바로 주소 검색 화면으로 넘어가는지 본다. 서버는 가짜(test/fakeHttp.js)다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingAddress from './BookingAddress.jsx';
import { ADDRESSES } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

function renderAddress() {
  return render(
    <MemoryRouter initialEntries={['/booking/home/address']}>
      <Routes>
        <Route path="/booking/:category/address" element={<BookingAddress />} />
        <Route path="/booking/:category/address-search" element={<p>주소 검색 화면</p>} />
        <Route path="/booking/:category/schedule" element={<p>날짜 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Booking/Address 주소 선택', () => {
  beforeEach(() => sessionStorage.clear());

  it('저장 주소가 있으면 기본 주소가 골라져 있고 다음을 누르면 그 주소로 날짜 화면에 간다', async () => {
    renderAddress();
    const radio = await screen.findByRole('radio', { name: new RegExp(ADDRESSES[0].label) });
    expect(radio).toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('날짜 화면')).toBeInTheDocument();
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.home'));
    expect(draft).toMatchObject({ reuseAddress: 'no', roadAddress: ADDRESSES[0].roadAddress });
  });

  it('저장 주소가 없으면 주소 검색 화면으로 넘어간다', async () => {
    const { __fake } = await import('../../api/http.js');
    const saved = __fake.addresses;
    __fake.addresses = [];
    renderAddress();
    expect(await screen.findByText('주소 검색 화면')).toBeInTheDocument();
    __fake.addresses = saved; // 이 파일은 가짜 서버 상태를 이어 쓰므로 되돌린다
  });
});
