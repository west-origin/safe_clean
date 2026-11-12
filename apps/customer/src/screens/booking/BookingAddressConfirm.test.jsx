import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import BookingAddressConfirm from './BookingAddressConfirm.jsx';
import { setBookingDraft } from './bookingDraft.js';
import { addAddress } from '../../api/customer.js';
vi.mock('../../api/customer.js', () => ({ addAddress: vi.fn() }));
beforeEach(() => { sessionStorage.clear(); vi.clearAllMocks(); });
it('선택 주소와 부가 정보를 표시하고 상세주소·별칭을 저장한 뒤 다음 단계로 간다', async () => {
  setBookingDraft('home', { roadAddress: '세종특별자치시 마음로 64 (고운동)', addressSub: '고운동 복합커뮤니티센터(남측)' });
  addAddress.mockResolvedValue({ id: 'new-address', label: '부모님 댁' });
  render(<MemoryRouter initialEntries={['/booking/home/address-confirm']}><Routes>
    <Route path="/booking/:category/address-confirm" element={<BookingAddressConfirm />} />
    <Route path="/booking/home/options" element={<p>옵션 화면</p>} />
  </Routes></MemoryRouter>);
  expect(screen.getByRole('heading', { name: '장소 정보' })).toBeInTheDocument();
  expect(screen.getByText('세종특별자치시 마음로 64 (고운동)')).toBeInTheDocument();
  expect(screen.getByText('고운동 복합커뮤니티센터(남측)')).toBeInTheDocument();
  expect(screen.queryByText('평수(공급면적)')).toBeNull();
  expect(screen.queryByText('연락처')).toBeNull();
  expect(screen.getByRole('button', { name: '다음' })).toBeEnabled(); // 상세주소는 비워도 된다 (단독주택)
  fireEvent.change(screen.getByRole('textbox', { name: '상세주소' }), { target: { value: '101호' } });
  fireEvent.change(screen.getByRole('textbox', { name: '장소 별칭' }), { target: { value: '부모님 댁' } });
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
  expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
  expect(addAddress).toHaveBeenCalledWith({ roadAddress: '세종특별자치시 마음로 64 (고운동)', detailAddress: '101호', label: '부모님 댁' });
  expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home'))).toMatchObject({ addressId: 'new-address', reuseAddress: 'no' });
});

function renderScreen() {
  render(<MemoryRouter initialEntries={['/booking/home/address-confirm']}><Routes>
    <Route path="/booking/:category/address-confirm" element={<BookingAddressConfirm />} />
    <Route path="/booking/home/options" element={<p>옵션 화면</p>} />
  </Routes></MemoryRouter>);
}

it('상세주소를 비우고 다음을 누르면 확인 시트 — 입력하기는 상세주소 칸으로 돌아간다', () => {
  setBookingDraft('home', { roadAddress: '서울 강남구 테헤란로 1' });
  renderScreen();
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
  const sheet = screen.getByRole('dialog', { name: '상세주소 없이 진행할까요?' });
  expect(sheet).toHaveTextContent('아파트·빌라라면 동·호수를 꼭 적어주세요. 매니저가 집을 찾는 데 필요해요.');
  fireEvent.click(screen.getByRole('button', { name: '입력하기' }));
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.getByRole('textbox', { name: '상세주소' })).toHaveFocus();
  expect(addAddress).not.toHaveBeenCalled();
});

it('확인 시트에서 없이 진행을 누르면 상세주소 없이 저장하고 다음 단계로 간다', async () => {
  setBookingDraft('home', { roadAddress: '서울 강남구 테헤란로 1', detailAddress: '  ' });
  addAddress.mockResolvedValue({ id: 'house', label: '기타' });
  renderScreen();
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
  fireEvent.click(screen.getByRole('button', { name: '없이 진행' }));
  expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
  expect(addAddress).toHaveBeenCalledWith({ roadAddress: '서울 강남구 테헤란로 1', detailAddress: '', label: '' });
});

it('상세주소를 적었으면 시트 없이 바로 넘어간다', async () => {
  setBookingDraft('home', { roadAddress: '서울 강남구 테헤란로 1', detailAddress: '101호' });
  addAddress.mockResolvedValue({ id: 'a', label: '기타' });
  renderScreen();
  fireEvent.click(screen.getByRole('button', { name: '다음' }));
  expect(screen.queryByRole('dialog')).toBeNull();
  expect(await screen.findByText('옵션 화면')).toBeInTheDocument();
});
