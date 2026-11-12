// 어드민 매니저 앱 로그인 흐름 (코드에서 정함, rbac.md §15-20): 비밀번호 로그인 → 인증앱 6자리 화면 → 관리자 홈.
// 서버 대신 test/fakeHttp.js — ADMIN_PHONE 으로 로그인하면 어드민(aal1 토큰), 6자리 VALID_CODE 면 aal2 토큰.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Login from './Login.jsx';
import MfaVerify from './MfaVerify.jsx';
import AdminHome from '../admin/AdminHome.jsx';
import RequireAuth from '../RequireAuth.jsx';
import { isAdmin, hasAdminMfa } from '../../api/manager.js';
import { session } from '../../api/session.js';
import { ADMIN_PHONE, VALID_CODE } from '../../test/fakeHttp.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/auth/login" element={<Login />} />
        <Route path="/auth/mfa" element={<MfaVerify />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<p>매니저 홈</p>} />
          <Route path="/admin" element={<AdminHome />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

async function loginAsAdmin() {
  renderAt('/auth/login');
  fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: ADMIN_PHONE } });
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } });
  fireEvent.click(screen.getByRole('button', { name: '로그인하기' }));
  return screen.findByRole('heading', { name: '관리자 인증' });
}

describe('어드민 로그인 → 인증앱 6자리 → 관리자 홈', () => {
  beforeEach(() => session.clear());

  it('어드민은 비밀번호 로그인 뒤 6자리 화면으로 가고, 6자리를 다 넣어야 확인이 켜진다', async () => {
    await loginAsAdmin();
    expect(isAdmin()).toBe(true);
    expect(hasAdminMfa()).toBe(false); // 아직 aal1 토큰
    const submit = screen.getByRole('button', { name: '확인' });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('인증앱 6자리'), { target: { value: '12345' } });
    expect(submit).toBeDisabled();
  });

  it('틀린 6자리면 안내 문구가 보이고 칸이 비워진다, 맞으면 관리자 홈', async () => {
    await loginAsAdmin();
    const field = screen.getByLabelText('인증앱 6자리');
    fireEvent.change(field, { target: { value: '000000' } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByText('인증번호가 맞지 않아요. 인증앱의 새 숫자를 입력해주세요.')).toBeInTheDocument();
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(field).toHaveValue('');

    fireEvent.change(field, { target: { value: VALID_CODE } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByRole('heading', { name: '관리자' })).toBeInTheDocument();
    expect(screen.getByText('운영자')).toBeInTheDocument();
    expect(hasAdminMfa()).toBe(true);
  });

  it('6자리 전에 매니저 화면 주소로 가도 6자리 화면으로 돌아온다', async () => {
    await loginAsAdmin();
    renderAt('/');
    expect((await screen.findAllByRole('heading', { name: '관리자 인증' })).length).toBeGreaterThan(0);
  });

  it('"다른 계정으로 로그인" 은 로그아웃하고 로그인 화면으로', async () => {
    await loginAsAdmin();
    fireEvent.click(screen.getByRole('button', { name: '다른 계정으로 로그인' }));
    expect(await screen.findByRole('button', { name: '로그인하기' })).toBeInTheDocument();
    expect(session.get()).toBeNull();
  });

  it('관리자 홈의 로그아웃은 세션을 지우고 로그인 화면으로', async () => {
    await loginAsAdmin();
    fireEvent.change(screen.getByLabelText('인증앱 6자리'), { target: { value: VALID_CODE } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }));
    expect(await screen.findByRole('button', { name: '로그인하기' })).toBeInTheDocument();
    expect(session.get()).toBeNull();
  });
});
