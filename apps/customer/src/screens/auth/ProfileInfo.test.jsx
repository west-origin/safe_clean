// 고객 가입 기본정보 화면(ProfileInfo)의 휴대폰 인증 흐름 테스트.
// 인증번호 발송 → 6자리 확인 → "다음" 으로 계정 생성까지, 그리고 이미 가입한 번호일 때의 로그인 안내를 확인한다.
// 서버 대신 test/fakeHttp.js: 인증번호는 '123456' 만 맞고, 번호 '00000000' 은 이미 가입한 번호로 취급한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import ProfileInfo from './ProfileInfo.jsx';
import { session } from '../../api/session.js';

// http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 화면이 부르는 customer.js 의 요청도 이 가짜 서버로 간다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 이동할 곳(주소·로그인 화면)은 글자만 있는 자리로 둔다 — 화면 이동은 그 <p> 글자로 확인한다
function renderInfo() {
  return render(
    <MemoryRouter initialEntries={['/auth/profile-info?method=phone']}>
      <Routes>
        <Route path="/auth/profile-info" element={<ProfileInfo />} />
        <Route path="/auth/profile-address" element={<p>주소 화면</p>} />
        <Route path="/auth/login" element={<p>로그인 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Auth/ProfileInfo 휴대폰 인증', () => {
  // 테스트끼리 섞이지 않게 세션과 가입 초안(localStorage)을 비운다
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    session.clear();
  });

  // 준비: 이름·닉네임·비밀번호·번호 입력 → 틀린 인증번호(111111) 다음 맞는 인증번호(123456).
  // 확인: 오류 문구 → 완료 문구 → "다음" 뒤 주소 화면, 세션은 토큰만 있고 가입 중(signupStep 'address')
  it('인증하기 → 인증번호 6자리 → 맞으면 "다음" 이 켜지고, 다음이 계정을 만들어 가입 중 로그인 상태가 된다', async () => {
    renderInfo();
    const next = await screen.findByRole('button', { name: '다음' });
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '김하늘' } });
    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: '하늘' } });
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password1' } });
    fireEvent.change(screen.getByLabelText('휴대폰 번호'), { target: { value: '12345678' } });
    expect(next).toBeDisabled(); // 인증 전
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    const code = await screen.findByLabelText('인증번호');
    fireEvent.change(code, { target: { value: '111111' } });
    expect(await screen.findByText('인증번호가 맞지 않아요')).toBeInTheDocument();
    fireEvent.change(code, { target: { value: '123456' } });
    expect(await screen.findByText('휴대폰 인증이 완료됐어요')).toBeInTheDocument();
    expect(next).toBeEnabled();
    fireEvent.click(next);
    expect(await screen.findByText('주소 화면')).toBeInTheDocument();
    expect(session.accessToken()).toBe('access'); // 가짜 서버가 준 토큰이 저장됐다
    expect(session.isSignedIn()).toBe(false); // 가입이 덜 끝나 아직 '로그인됨' 은 아니다
    expect(session.signupStep()).toBe('address'); // 다음 가입 단계
  });

  it('이미 가입한 번호면 로그인 안내가 뜬다', async () => {
    renderInfo();
    fireEvent.change(await screen.findByLabelText('휴대폰 번호'), { target: { value: '00000000' } }); // 가짜 서버의 TAKEN_PHONE
    fireEvent.click(screen.getByRole('button', { name: '인증하기' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('이미 가입한 번호예요');
    fireEvent.click(screen.getByRole('button', { name: '로그인하기' }));
    expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
  });
});
