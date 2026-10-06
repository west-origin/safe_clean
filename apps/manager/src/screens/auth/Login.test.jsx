// 매니저 로그인 화면(Login.jsx) 테스트: 버튼 활성 조건, Enter 로 로그인, 원래 주소로 복귀, 비밀번호 틀림, 회원가입 이동.
// 서버 대신 test/fakeHttp.js 의 가짜 서버를 쓴다 — 비밀번호가 'wrong-password' 면 LOGIN_FAILED, 그 밖은 로그인 성공.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Login from './Login.jsx';
import { isSignedIn } from '../../api/manager.js';
import { session } from '../../api/session.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 로그인 화면과 이동 목적지(문구만 있는 가짜 화면)를 둔 라우터로 그린다.
// MemoryRouter = 브라우저 주소창 대신 메모리에 방문 기록을 두는 라우터 (테스트용)
function renderLogin(entry = '/auth/login') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/auth/login" element={<Login />} />
        <Route path="/auth/signup" element={<p>가입 화면</p>} />
        <Route path="/" element={<p>홈 화면</p>} />
        <Route path="/settlement" element={<p>정산 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Manager/Auth/Login 흐름', () => {
  beforeEach(() => session.clear()); // 테스트마다 로그인 세션을 비운다 (앞 테스트의 로그인 상태가 남지 않게)

  it('번호와 비밀번호를 모두 넣어야 "로그인하기" 가 켜지고, 성공하면 로그인 상태로 홈에 간다', async () => {
    renderLogin();
    const submit = await screen.findByRole('button', { name: '로그인하기' }); // 문구를 불러온 뒤에야 버튼이 그려지므로 기다린다
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } });
    expect(submit).toBeEnabled();
    fireEvent.click(submit);
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
    expect(isSignedIn()).toBe(true);
  });

  it('비밀번호 칸에서 Enter(키보드의 이동) 로도 로그인한다 (직접 사용 점검 U7)', async () => {
    renderLogin();
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    const password = screen.getByLabelText('비밀번호');
    fireEvent.change(password, { target: { value: 'password1' } });
    fireEvent.keyDown(password, { key: 'Enter' });
    expect(await screen.findByText('홈 화면')).toBeInTheDocument();
  });

  it('RequireAuth 가 넘긴 원래 주소로 돌아간다', async () => {
    // RequireAuth 가 하듯 state.from='/settlement' 를 달고 로그인 화면을 연다 → 로그인 뒤 정산 화면이 보여야 한다
    render(
      <MemoryRouter initialEntries={[{ pathname: '/auth/login', state: { from: '/settlement' } }]}>
        <Routes>
          <Route path="/auth/login" element={<Login />} />
          <Route path="/settlement" element={<p>정산 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } });
    fireEvent.click(screen.getByRole('button', { name: '로그인하기' }));
    await waitFor(() => expect(screen.getByText('정산 화면')).toBeInTheDocument()); // waitFor: 조건이 맞을 때까지 되풀이해 확인한다
  });

  it('비밀번호가 틀리면 문구를 보여주고 이동하지 않는다', async () => {
    renderLogin();
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-password' } }); // 가짜 서버가 LOGIN_FAILED 로 답하는 값
    fireEvent.click(screen.getByRole('button', { name: '로그인하기' }));
    expect(await screen.findByText('휴대폰 번호 또는 비밀번호를 확인해주세요.')).toBeInTheDocument();
    expect(isSignedIn()).toBe(false);
  });

  it('"회원가입" 링크는 가입 화면으로 간다', async () => {
    renderLogin();
    fireEvent.click(await screen.findByRole('button', { name: '회원가입' }));
    expect(await screen.findByText('가입 화면')).toBeInTheDocument();
  });
});
