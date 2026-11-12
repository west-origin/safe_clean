import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import SignUp from './SignUp.jsx';
import { socialLogin } from '../../auth/socialLogin.js';
import { refreshMe } from '../../api/customer.js';
vi.mock('../../auth/socialLogin.js', () => ({ socialLogin: vi.fn(), loginError: () => '로그인을 취소했어요. 다시 선택할 수 있어요.' }));
vi.mock('../../api/customer.js', () => ({ refreshMe: vi.fn(), isSignedIn: () => false }));
function show() { return render(<MemoryRouter initialEntries={['/signup']}><Routes><Route path="/signup" element={<SignUp />} /><Route path="/home" element={<p>홈 화면</p>} /><Route path="/auth/terms" element={<p>약관 화면</p>} /></Routes></MemoryRouter>); }
describe('social sign in', () => {
  beforeEach(() => vi.resetAllMocks());
  it('shows only Kakao and Google login and no direct credentials', () => {
    show();
    expect(screen.getByRole('button', { name: /카카오 계정으로 로그인/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /구글 계정으로 로그인/ })).toBeEnabled();
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByText(/비밀번호|네이버|애플/)).toBeNull();
  });
  it('prevents duplicate requests and returns home after success', async () => {
    let done;
    socialLogin.mockImplementation(() => new Promise(resolve => { done = resolve; }));
    refreshMe.mockResolvedValue({ role: 'customer', profileComplete: true });
    show();
    const kakao = screen.getByRole('button', { name: /카카오 계정으로 로그인/ });
    fireEvent.click(kakao); fireEvent.click(kakao);
    expect(socialLogin).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: /구글 계정으로 로그인/ })).toBeDisabled();
    done({ redirecting: false });
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  });
  it('allows retry after cancellation', async () => {
    socialLogin.mockRejectedValue({ code: 'CANCELLED' });
    show(); fireEvent.click(screen.getByRole('button', { name: /구글 계정으로 로그인/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('로그인을 취소');
    expect(screen.getByRole('button', { name: /구글 계정으로 로그인/ })).toBeEnabled();
  });
  it('uses official account recovery destinations', () => {
    show(); fireEvent.click(screen.getByRole('button', { name: '로그인에 문제가 있나요?' }));
    expect(screen.getByRole('link', { name: '카카오 계정 복구' })).toHaveAttribute('href', 'https://accounts.kakao.com');
    expect(screen.getByRole('link', { name: '구글 계정 복구' })).toHaveAttribute('href', 'https://accounts.google.com/signin/recovery');
  });
});
