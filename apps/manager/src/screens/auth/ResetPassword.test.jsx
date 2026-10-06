// 비밀번호 재설정 화면(ResetPassword.jsx) 테스트: 성공 흐름, 가입되지 않은 번호, 토큰 소비 경합, 자동 로그인 실패, 인증번호 재확인.
// 가짜 서버(test/fakeHttp.js) 규칙: 인증번호는 '123456' 만 맞다, '99999999' 는 가입되지 않은 번호,
// RACED_RESET_PASSWORD 는 토큰이 이미 쓰인 것처럼 OTP_EXPIRED, SESSION_LOST_RESET_PASSWORD 는 PASSWORD_CHANGED 로 답한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import ResetPassword from './ResetPassword.jsx';
import Login from './Login.jsx';
import { session } from '../../api/session.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 재설정 화면 + 실제 로그인 화면(PASSWORD_CHANGED 때 넘긴 안내가 보이는지 확인용) + 홈 자리
function renderScreen() {
  return render(
    <MemoryRouter initialEntries={['/auth/reset-password']}>
      <Routes>
        <Route path="/auth/reset-password" element={<ResetPassword />} />
        <Route path="/auth/login" element={<Login />} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Manager/Auth/ResetPassword', () => {
  // 테스트마다 저장소(가입 초안 등)와 로그인 세션을 비운다
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session.clear();
  });

  it('인증 → 새 비밀번호를 바꾸면 로그인 상태로 홈에 간다', async () => {
    // 가입 초안을 심어 두고 → 재설정 성공 뒤 홈 이동·로그인 상태·초안 삭제를 확인한다
    localStorage.setItem('ansimclean.manager.signup', JSON.stringify({ agreedTermIds: ['recording'] })); // 이 기기에서 앞사람이 남긴 가입 초안
    renderScreen();
    const submit = await screen.findByRole('button', { name: '비밀번호 변경하기' });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    fireEvent.change(await screen.findByLabelText('인증번호'), { target: { value: '123456' } });
    fireEvent.change(await screen.findByLabelText('새 비밀번호'), { target: { value: 'newpassword1' } });
    fireEvent.click(submit);
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(session.isSignedIn()).toBe(true);
    expect(localStorage.getItem('ansimclean.manager.signup')).toBeNull(); // 앞사람의 약관 동의로 가입을 끝내지 않게 (자체 점검 #47)
  });

  it('가입되지 않은 번호면 안내가 뜬다', async () => {
    renderScreen();
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '99999999' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('가입되지 않은 번호예요');
  });

  it('토큰이 이미 소비됐으면(동시 요청) 인증부터 다시 하게 한다', async () => {
    const { RACED_RESET_PASSWORD } = await import('../../test/fakeHttp.js'); // 가짜 서버가 '토큰이 이미 쓰였다'(OTP_EXPIRED)로 답하는 비밀번호
    renderScreen();
    const submit = await screen.findByRole('button', { name: '비밀번호 변경하기' });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    fireEvent.change(await screen.findByLabelText('인증번호'), { target: { value: '123456' } });
    fireEvent.change(await screen.findByLabelText('새 비밀번호'), { target: { value: RACED_RESET_PASSWORD } });
    fireEvent.click(submit);
    expect(await screen.findByRole('alert')).toHaveTextContent('휴대폰 인증을 다시 해주세요');
    expect(screen.queryByLabelText('새 비밀번호')).not.toBeInTheDocument(); // 죽은 토큰으로 다시 보내지 못하게
    expect(submit).toBeDisabled();
    expect(screen.getByLabelText('휴대폰 번호')).toBeEnabled(); // 번호 입력이 다시 열린다
  });

  it('비밀번호는 바뀌었는데 자동 로그인만 실패하면 로그인 화면으로 가서 새 비밀번호로 로그인하라고 알린다 (자체 점검 #22)', async () => {
    const { SESSION_LOST_RESET_PASSWORD } = await import('../../test/fakeHttp.js'); // 가짜 서버가 PASSWORD_CHANGED 로 답하는 비밀번호
    renderScreen();
    const submit = await screen.findByRole('button', { name: '비밀번호 변경하기' });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    fireEvent.change(await screen.findByLabelText('인증번호'), { target: { value: '123456' } });
    fireEvent.change(await screen.findByLabelText('새 비밀번호'), { target: { value: SESSION_LOST_RESET_PASSWORD } });
    fireEvent.click(submit);
    expect(await screen.findByText('비밀번호가 변경됐어요. 새 비밀번호로 로그인해주세요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '비밀번호 변경하기' })).toBeNull(); // 로그인 화면
  });

  it('확인 중에 고친 인증번호는 앞 확인이 끝난 뒤 다시 확인한다 (자체 점검 #37)', async () => {
    // 틀린 6자리의 확인 응답이 오기 전에 맞는 6자리로 고친다 → 앞 확인이 끝난 뒤 새 값으로 다시 확인해 새 비밀번호 칸이 열려야 한다
    renderScreen();
    await screen.findByRole('button', { name: '비밀번호 변경하기' });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    const code = await screen.findByLabelText('인증번호');
    fireEvent.change(code, { target: { value: '123457' } }); // 오타 — 확인 요청이 나간다
    fireEvent.change(code, { target: { value: '123456' } }); // 응답 전에 고쳤다
    expect(await screen.findByLabelText('새 비밀번호')).toBeInTheDocument(); // 예전: 앞 요청의 '맞지 않아요' 만 남았다
  });
});
