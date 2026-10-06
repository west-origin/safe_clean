// 매니저 회원가입 3단계(약관 SignUpTerms → 정보 SignUpInfo → 완료 SignUpComplete) 화면 테스트.
// 가짜 서버(test/fakeHttp.js): 인증번호는 '123456' 만 맞고, 가입 완료 요청은 필수 약관 id 가 모두 있어야 받는다.
// 가입 초안(약관 동의·인증 토큰)은 localStorage 'ansimclean.manager.signup' 에 있다 (api/manager.js).
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import SignUpTerms from './SignUpTerms.jsx';
import SignUpInfo from './SignUpInfo.jsx';
import SignUpComplete from './SignUpComplete.jsx';
import { isSignedIn } from '../../api/manager.js';
import { session } from '../../api/session.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 가입 화면 셋과 약관 본문·홈 자리(문구만 있는 가짜 화면)를 둔 라우터. entry 로 시작 화면을 고른다
function renderFlow(entry) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/auth/signup" element={<SignUpTerms />} />
        <Route path="/auth/signup/info" element={<SignUpInfo />} />
        <Route path="/auth/signup/complete" element={<SignUpComplete />} />
        <Route path="/auth/terms/:id" element={<p>약관 본문</p>} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Manager/Auth/SignUp 흐름', () => {
  // 테스트마다 가입 초안(localStorage)과 로그인 세션을 비운다
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session.clear();
  });

  it('약관: 필수 4개를 모두 켜야 "다음" 이 켜지고 정보 입력으로 간다', async () => {
    renderFlow('/auth/signup');
    const next = await screen.findByRole('button', { name: '다음' });
    expect(next).toBeDisabled();
    ['(필수) 매니저 서비스 이용약관', '(필수) 개인정보 수집 및 이용 동의', '(필수) 위치기반 서비스 이용 동의'].forEach((name) => fireEvent.click(screen.getByRole('checkbox', { name })));
    expect(next).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: '(필수) 실시간 촬영 및 영상 녹화 동의' }));
    expect(next).toBeEnabled();
    fireEvent.click(next);
    expect(await screen.findByText('매니저 정보를 입력해주세요')).toBeInTheDocument();
  });

  it('약관: 전체 동의하기는 5개를 모두 켜고 끈다, "보기>" 는 약관 본문으로 간다', async () => {
    renderFlow('/auth/signup');
    const all = await screen.findByRole('checkbox', { name: '전체 동의하기' });
    fireEvent.click(all);
    expect(screen.getAllByRole('checkbox').every((c) => c.checked)).toBe(true);
    fireEvent.click(all);
    expect(screen.getAllByRole('checkbox').some((c) => c.checked)).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: '(필수) 매니저 서비스 이용약관 보기' }));
    expect(await screen.findByText('약관 본문')).toBeInTheDocument();
  });

  it('정보: 휴대폰 인증(6자리) + 비밀번호 8자 이상 + 확인 일치 + 이름이 있어야 "다음", 완료 화면의 "시작하기" 로 로그인 상태가 된다', async () => {
    // 1단계(필수 약관 동의)를 마친 것처럼 초안을 심고 정보 화면부터 시작한다 — 완료 화면의 "시작하기" 가 이 동의를 보낸다
    localStorage.setItem('ansimclean.manager.signup', JSON.stringify({ agreedTermIds: ['service', 'privacy', 'location', 'recording'] }));
    renderFlow('/auth/signup/info');
    const next = await screen.findByRole('button', { name: '다음' });
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '김매니저' } });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    // 인증하기 → 인증번호 필드 → 틀린 번호는 오류, 맞으면 완료
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    const codeField = await screen.findByLabelText('인증번호');
    fireEvent.change(codeField, { target: { value: '000000' } });
    expect(await screen.findByText('인증번호가 맞지 않아요')).toBeInTheDocument();
    fireEvent.change(codeField, { target: { value: '123456' } });
    expect(await screen.findByText('휴대폰 인증이 완료됐어요')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'short' } });
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'short' } });
    await waitFor(() => expect(next).toBeDisabled()); // 비밀번호 검사(checkPassword)가 비동기라 waitFor 로 결과가 반영될 때까지 기다린다
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } });
    expect(await screen.findByText('사용가능한 비밀번호입니다!')).toBeInTheDocument();
    expect(screen.getByText('비밀번호가 일치하지 않아요')).toBeInTheDocument();
    expect(next).toBeDisabled();
    fireEvent.change(screen.getByLabelText('비밀번호 확인'), { target: { value: 'password1' } });
    await waitFor(() => expect(next).toBeEnabled());
    fireEvent.click(next);
    expect(await screen.findByText('가입이 완료되었어요')).toBeInTheDocument();
    expect(isSignedIn()).toBe(false); // 토큰은 있지만 가입이 덜 끝남
    expect(session.signupStep()).toBe('complete');
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(isSignedIn()).toBe(true);
  });
});
