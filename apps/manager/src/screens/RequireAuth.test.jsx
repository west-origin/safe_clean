// RequireAuth 테스트: 세션 상태(로그인 전 / 가입 완료 / 가입 도중)에 따라
// 로그인 화면(원래 주소를 state.from 으로), 보호된 화면, 가입 완료 화면 중 어디가 보이는지 본다.
// 서버 없이 session.set 으로 로그인 상태를 직접 만든다.
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import RequireAuth from './RequireAuth.jsx';
import { session } from '../api/session.js';

/** 로그인 화면 대역 — RequireAuth 가 넘긴 state.from 을 글자로 보여 준다 */
function Login() {
  const { state } = useLocation();
  return <p>로그인 화면 (from: {state?.from})</p>;
}

/** path 에서 시작해 그린다. MemoryRouter = 주소창 없이 메모리 안에서만 도는 라우터 (테스트용). '/' 만 RequireAuth 안에 둔다 */
function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/auth/login" element={<Login />} />
        <Route path="/auth/signup/complete" element={<p>가입 완료 화면</p>} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<p>홈 화면</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('매니저 RequireAuth', () => {
  beforeEach(() => session.clear()); // 테스트마다 로그인 전 상태에서 시작

  it('로그인 전이면 로그인 화면으로 보내며 원래 주소를 넘긴다', () => {
    renderAt('/?scenario=empty'); // 쿼리까지 from 에 담기는지 보려고 쿼리를 붙인다
    expect(screen.getByText('로그인 화면 (from: /?scenario=empty)')).toBeInTheDocument();
  });

  it('로그인 후면 그대로 보여준다', () => {
    // profileComplete: true = 가입까지 끝난 세션 → isSignedIn() 이 true
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: { id: 'm1', role: 'manager', name: '김매니저', profileComplete: true, signupStep: null } });
    renderAt('/');
    expect(screen.getByText('홈 화면')).toBeInTheDocument();
  });

  it('가입이 덜 끝났으면 완료 화면으로', () => {
    // 토큰은 있지만 profileComplete: false, signupStep: 'complete' → isSignedIn() 은 false, 가입 완료 화면으로 보낸다
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: { id: 'm1', role: 'manager', name: '김매니저', profileComplete: false, signupStep: 'complete' } });
    renderAt('/');
    expect(screen.getByText('가입 완료 화면')).toBeInTheDocument();
  });
});
