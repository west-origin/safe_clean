// 첫 화면(SignUp) 테스트 (2026-11-03 로그인 시안): 로고·입력칸·SNS 버튼이 그려지고,
// 번호·비밀번호를 넣어야 '로그인' 이 켜지며, 성공하면 홈, 불일치면 문구, '회원가입' 은 약관 동의, SNS 는 안내 시트.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import SignUp from './SignUp.jsx';
import { WRONG_PASSWORD } from '../../test/fakeHttp.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const renderScreen = () =>
  render(
    <MemoryRouter initialEntries={['/signup']}>
      <Routes>
        <Route path="/signup" element={<SignUp />} />
        <Route path="/home" element={<p>홈 화면</p>} />
        <Route path="/auth/terms" element={<p>약관 화면</p>} />
        <Route path="/auth/profile-address" element={<p>주소 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );

describe('Auth/SignUp (첫 화면)', () => {
  beforeEach(() => localStorage.clear());

  it('로고·입력칸·SNS 버튼이 있고, 번호·비밀번호를 넣어야 로그인이 켜진다', () => {
    renderScreen();
    expect(screen.getByRole('img', { name: '안심클린' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('전화번호를 입력해주세요')).toHaveAttribute('type', 'tel');
    expect(screen.getByText('SNS계정으로 간편로그인하세요.')).toBeInTheDocument();
    ['카카오톡으로 로그인', '구글로 로그인', '네이버로 로그인'].forEach((name) => expect(screen.getByRole('button', { name })).toBeInTheDocument());
    const login = screen.getByRole('button', { name: '로그인' });
    expect(login).toBeDisabled();
    fireEvent.change(screen.getByLabelText('전화번호를 입력해주세요'), { target: { value: '010-1234-5678' } });
    fireEvent.change(screen.getByLabelText('비밀번호 입력'), { target: { value: 'password1' } });
    expect(login).toBeEnabled();
  });

  it('비밀번호가 틀리면 문구를 보이고, 맞으면 다음 화면으로 간다', async () => {
    renderScreen();
    fireEvent.change(screen.getByLabelText('전화번호를 입력해주세요'), { target: { value: '01012345678' } });
    fireEvent.change(screen.getByLabelText('비밀번호 입력'), { target: { value: WRONG_PASSWORD } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('휴대폰 번호 또는 비밀번호를 확인해주세요.');
    fireEvent.change(screen.getByLabelText('비밀번호 입력'), { target: { value: 'password1' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByText(/홈 화면|주소 화면/)).toBeInTheDocument();
  });

  it("'회원가입' 은 약관 동의로, SNS 버튼은 안내 시트를 연다", async () => {
    renderScreen();
    fireEvent.click(screen.getByRole('button', { name: '카카오톡으로 로그인' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('지금은 전화번호로만 가입할 수 있어요');
    fireEvent.click(within(dialog).getAllByRole('button', { name: '닫기' }).at(-1));
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }));
    expect(await screen.findByText('약관 화면')).toBeInTheDocument();
  });
});
