// 주소 수정 화면(BookingAddressEdit) 테스트: 도로명주소는 못 고치고 별칭·상세주소만 저장되는지, 삭제 확인 시트로 지워지는지 본다.
// 서버는 가짜(test/fakeHttp.js) — 이 파일은 가짜 서버 주소 목록을 이어 쓰므로 순서대로 본다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingAddressEdit from './BookingAddressEdit.jsx';
import { ADDRESSES } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const id = ADDRESSES[0].id;
function renderEdit() {
  return render(
    <MemoryRouter initialEntries={['/booking/home/address', `/booking/home/address-edit/${id}`]} initialIndex={1}>
      <Routes>
        <Route path="/booking/:category/address-edit/:id" element={<BookingAddressEdit />} />
        <Route path="/booking/:category/address" element={<p>주소 선택 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Booking/AddressEdit 주소 수정', () => {
  beforeEach(() => sessionStorage.clear());

  it('별칭·상세주소만 고쳐 저장한다', async () => {
    renderEdit();
    expect(await screen.findByRole('textbox', { name: '주소' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: '주소 이름' }), { target: { value: '우리' } });
    fireEvent.change(screen.getByRole('textbox', { name: '상세주소' }), { target: { value: '101동 1001호' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('주소 선택 화면')).toBeInTheDocument();
    const { __fake } = await import('../../api/http.js');
    expect(__fake.addresses.find((a) => a.id === id)).toMatchObject({ label: '우리', detailAddress: '101동 1001호', roadAddress: ADDRESSES[0].roadAddress });
  });

  it('주소 삭제는 확인 시트를 거쳐 지운다', async () => {
    renderEdit();
    fireEvent.click(await screen.findByRole('button', { name: '주소 삭제' }));
    fireEvent.click(await screen.findByRole('button', { name: '삭제' }));
    expect(await screen.findByText('주소 선택 화면')).toBeInTheDocument();
    const { __fake } = await import('../../api/http.js');
    expect(__fake.addresses.some((a) => a.id === id)).toBe(false);
  });
});
