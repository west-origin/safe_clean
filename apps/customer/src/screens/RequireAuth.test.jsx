// RequireAuth 테스트: 로그인 전 → /signup(원래 주소를 state.from 으로), 로그인 후 → 그대로, 가입 중 → 남은 가입 화면.
// 실제 화면 대신 글자만 있는 가짜 라우트를 MemoryRouter(주소창 없는 메모리 라우터)에 두고, 보이는 글자로 어디로 갔는지 판단한다.
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import RequireAuth from './RequireAuth.jsx';
import { session } from '../api/session.js';

/** 가짜 가입 화면: RequireAuth 가 넘긴 state.from 을 글자로 보여 준다 */
function SignUp() {
  const { state } = useLocation();
  return <p>가입 화면 (from: {state?.from})</p>;
}

/** path 에서 시작하는 라우터를 그린다. /home 만 RequireAuth 안에 있다 */
function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/signup" element={<SignUp />} />
        <Route path="/auth/profile-housing" element={<p>주거 정보 화면</p>} />
        <Route element={<RequireAuth />}>
          <Route path="/home" element={<p>홈 화면</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

// 가입까지 끝난 사용자 (signupStep 이 null)
const fullUser = { id: 'u1', role: 'customer', name: '김하늘', profileComplete: true, signupStep: null };

describe('RequireAuth', () => {
  beforeEach(() => session.clear()); // 매번 로그아웃 상태에서 시작한다

  it('로그인 전이면 가입 화면으로 보내며 원래 주소를 넘긴다', () => {
    renderAt('/home?tab=1');
    expect(screen.getByText('가입 화면 (from: /home?tab=1)')).toBeInTheDocument();
  });

  it('로그인 후면 그대로 보여준다', () => {
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: fullUser });
    renderAt('/home');
    expect(screen.getByText('홈 화면')).toBeInTheDocument();
  });

  it('가입이 덜 끝난 계정은 남은 가입 화면으로 보낸다', () => {
    session.set({ accessToken: 'a', refreshToken: 'r', expiresIn: 3600, user: { ...fullUser, profileComplete: false, signupStep: 'housing' } });
    renderAt('/home');
    expect(screen.getByText('주거 정보 화면')).toBeInTheDocument();
  });
});
