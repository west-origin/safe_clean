// 비밀번호 재설정 화면(ResetPassword) 테스트: 정상 변경, 가입되지 않은 번호, 이미 쓰인 토큰,
// 비밀번호는 바뀌고 세션 발급만 실패한 경우, 확인 중에 고친 인증번호를 확인한다.
// 서버 대신 test/fakeHttp.js: 인증번호 '123456' 만 맞고, '99999999' 는 가입되지 않은 번호,
// 특정 비밀번호(RACED_RESET_PASSWORD·SESSION_LOST_RESET_PASSWORD)는 각각 정해진 실패를 흉내 낸다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import ResetPassword from './ResetPassword.jsx';
import LoginPhone from './LoginPhone.jsx';
import { session } from '../../api/session.js';

// http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 화면이 부르는 customer.js 의 요청도 이 가짜 서버로 간다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 로그인 화면은 진짜 LoginPhone 을 붙인다 — 넘겨받은 안내(state.notice)가 실제로 보이는지 확인하려고. 나머지 목적지는 글자만
function renderScreen() {
  return render(
    <MemoryRouter initialEntries={['/auth/reset-password']}>
      <Routes>
        <Route path="/auth/reset-password" element={<ResetPassword />} />
        <Route path="/auth/login" element={<LoginPhone />} />
        <Route path="/home" element={<p>홈 화면</p>} />
        <Route path="/signup" element={<p>가입 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Auth/ResetPassword', () => {
  // 테스트끼리 섞이지 않게 세션과 가입 초안(localStorage)을 비운다
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session.clear();
  });

  // 준비: 앞사람의 가입 초안을 남겨 두고 인증 → 짧은 비밀번호 → 8자 이상.
  // 확인: 짧으면 버튼이 꺼져 있고, 바꾼 뒤 홈·로그인 상태·가입 초안 삭제
  it('인증 → 새 비밀번호 8자 이상이면 바꾸고 로그인 상태로 홈에 간다', async () => {
    localStorage.setItem('ansimclean.signup', JSON.stringify({ agreedTermIds: ['marketing'] })); // 이 기기에서 앞사람이 남긴 가입 초안
    renderScreen();
    const submit = await screen.findByRole('button', { name: '비밀번호 변경하기' });
    expect(submit).toBeDisabled();

    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    fireEvent.change(await screen.findByLabelText('인증번호'), { target: { value: '123456' } });

    const password = await screen.findByLabelText('새 비밀번호');
    expect(screen.getByText('8자 이상 입력해주세요')).toBeInTheDocument(); // 규칙을 미리 보여 준다
    fireEvent.change(password, { target: { value: 'short' } });
    expect(submit).toBeDisabled();

    fireEvent.change(password, { target: { value: 'newpassword1' } });
    expect(submit).toBeEnabled();
    fireEvent.click(submit);

    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(session.isSignedIn()).toBe(true);
    expect(localStorage.getItem('ansimclean.signup')).toBeNull(); // 앞사람의 약관 동의로 가입을 끝내지 않게 (자체 점검 #39)
  });

  it('가입되지 않은 번호면 안내와 회원가입 링크가 뜬다', async () => {
    renderScreen();
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '99999999' } }); // 가짜 서버의 UNKNOWN_PHONE
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('가입되지 않은 번호예요');
    fireEvent.click(screen.getByRole('button', { name: '회원가입하기' }));
    expect(await screen.findByText('가입 화면')).toBeInTheDocument();
  });

  // 준비: RACED_RESET_PASSWORD 로 보내면 가짜 서버가 OTP_EXPIRED 를 던진다(다른 요청이 토큰을 먼저 쓴 상황).
  // 확인: 오류 문구가 뜨고, 새 비밀번호 칸이 사라지고, 번호 입력이 다시 열린다
  it('토큰이 이미 소비됐으면(동시 요청) 인증부터 다시 하게 한다', async () => {
    const { RACED_RESET_PASSWORD } = await import('../../test/fakeHttp.js');
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

  // 준비: SESSION_LOST_RESET_PASSWORD 로 보내면 가짜 서버가 PASSWORD_CHANGED 를 던진다.
  // 확인: 진짜 LoginPhone 으로 이동해 넘겨받은 안내 문구를 보인다
  it('비밀번호는 바뀌었는데 자동 로그인만 실패하면 로그인 화면으로 가서 새 비밀번호로 로그인하라고 알린다 (자체 점검 #22)', async () => {
    const { SESSION_LOST_RESET_PASSWORD } = await import('../../test/fakeHttp.js');
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

  // 준비: 오타 6자리로 확인 요청이 나간 사이(응답 전) 맞는 인증번호로 고친다.
  // 확인: 두 번째 값이 앞 요청이 끝난 뒤 확인되어 새 비밀번호 칸이 나타난다
  it('확인 중에 고친 인증번호는 앞 확인이 끝난 뒤 다시 확인한다 (자체 점검 #37)', async () => {
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
