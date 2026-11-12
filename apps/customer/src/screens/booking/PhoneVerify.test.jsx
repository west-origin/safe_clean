// 첫 1회 청소 휴대폰 인증 화면(PhoneVerify, 2026-11-10 시안 2장): 번호 → 전송 성공 시에만 2단계 → 6자리 자동 확인 → 주기 화면 복귀.
// 서버는 가짜(test/fakeHttp.js): 맞는 인증번호 VALID_CODE, 다른 계정 번호 TAKEN_PHONE. 실제 서버는 Supabase phone_change (docs/auth/phone-otp.md)
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import PhoneVerify from './PhoneVerify.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const renderScreen = () => render(
  <MemoryRouter initialEntries={['/booking/home/phone-verify']}>
    <Routes>
      <Route path="/booking/:category/phone-verify" element={<PhoneVerify />} />
      <Route path="/booking/:category/frequency" element={<p>주기 화면</p>} />
    </Routes>
  </MemoryRouter>,
);
const phoneInput = () => screen.getByRole('textbox', { name: '휴대폰 번호' });

describe('PhoneVerify (코드에서 정함, 시안 2장)', () => {
  it('번호를 하이픈 꼴로 보이고, 올바른 번호일 때만 보내기가 켜진다', () => {
    renderScreen();
    expect(screen.getByRole('heading', { name: '휴대폰 번호 인증' })).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('개인정보 보호정책에 따라 안전하게');
    const send = screen.getByRole('button', { name: '인증번호 보내기' });
    expect(send).toBeDisabled();
    fireEvent.change(phoneInput(), { target: { value: '0100000' } });
    expect(send).toBeDisabled();
    fireEvent.change(phoneInput(), { target: { value: '01000000001' } });
    expect(phoneInput()).toHaveValue('010-0000-0001');
    expect(send).toBeEnabled();
  });

  it('전송이 성공하면 2단계로 가고, 6자리를 치면 자동으로 확인해 주기 화면으로 돌아간다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.phoneVerified = false;
    renderScreen();
    fireEvent.change(phoneInput(), { target: { value: '010-0000-0001' } });
    fireEvent.click(screen.getByRole('button', { name: '인증번호 보내기' }));
    expect(await screen.findByRole('heading', { name: '인증번호를 입력해주세요' })).toBeInTheDocument();
    expect(screen.getByText(/010-0000-0001로/)).toBeInTheDocument();
    expect(screen.getByText(/^0[23]:\d\d$/)).toBeInTheDocument(); // 3분 타이머 (03:00 또는 02:59)
    expect(screen.getByRole('button', { name: '인증번호 다시 받기' })).toBeDisabled(); // 60초 대기
    expect(__fake.calls.at(-1)).toMatchObject({ path: '/customer/identity/phone/otp', body: { phone: '01000000001' } });

    const code = screen.getByRole('textbox', { name: '인증번호' });
    expect(screen.getByRole('button', { name: '인증하고 계속하기' })).toBeDisabled();
    fireEvent.change(code, { target: { value: '000000' } }); // 6자리 → 자동 확인 → 틀림
    expect(await screen.findByText('인증번호가 맞지 않아요')).toBeInTheDocument();
    expect(code).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('button', { name: '인증하고 계속하기' })).toBeEnabled(); // 6자리면 버튼도 켜짐

    fireEvent.change(code, { target: { value: '123456' } });
    expect(await screen.findByText('주기 화면')).toBeInTheDocument();
    expect(__fake.phoneVerified).toBe(true);
  });

  it('전송에 실패하면 1단계에 머물고 오류를 보인다', async () => {
    const { TAKEN_PHONE } = await import('../../test/fakeHttp.js');
    renderScreen();
    fireEvent.change(phoneInput(), { target: { value: `010${TAKEN_PHONE}` } });
    fireEvent.click(screen.getByRole('button', { name: '인증번호 보내기' }));
    expect(await screen.findByText(/이 번호로는 인증할 수 없어요/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '휴대폰 번호 인증' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '인증번호' })).toBeNull();
  });

  it('휴대폰 번호 수정하기는 번호를 남긴 채 1단계로 돌아간다', async () => {
    renderScreen();
    fireEvent.change(phoneInput(), { target: { value: '01000000002' } });
    fireEvent.click(screen.getByRole('button', { name: '인증번호 보내기' }));
    fireEvent.click(await screen.findByRole('button', { name: '휴대폰 번호 수정하기' }));
    expect(phoneInput()).toHaveValue('010-0000-0002');
  });
});
