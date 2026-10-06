// 약관 동의 화면(TermsConsent) 테스트: 필수 약관을 다 켜야 "다음" 이 열리는지, "전체 동의하기" 가 모두 켜고 끄는지 본다.
// 약관 목록은 가짜 서버(test/fakeHttp.js)가 mock.js 의 TERMS(필수 3 + 선택 2)를 돌려준다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import TermsConsent from './TermsConsent.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

// 약관 화면을 ?method=kakao 로 연다. "다음" 이 가는 /auth/profile-info 는 글자만 있는 가짜 화면으로 둔다.
// MemoryRouter: 브라우저 주소창 없이 메모리 안에서 경로를 흉내 내는 라우터
function renderTerms() {
  return render(
    <MemoryRouter initialEntries={['/auth/terms?method=kakao']}>
      <Routes>
        <Route path="/auth/terms" element={<TermsConsent />} />
        <Route path="/auth/profile-info" element={<p>다음 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Auth/TermsConsent 흐름', () => {
  it('필수 약관 3개를 모두 체크해야 "다음" 이 활성화되고 method 를 이어서 넘긴다', async () => {
    renderTerms();
    // 약관 목록을 받기 전에는 로딩 화면이라 "다음" 버튼이 아직 없다 — 나타날 때까지 기다린다
    const next = await screen.findByRole('button', { name: '다음' });
    expect(next).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: '(필수) 서비스 이용약관' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '(필수) 개인정보 수집 및 이용 동의' }));
    expect(next).toBeDisabled();
    fireEvent.click(screen.getByRole('checkbox', { name: '(필수) 위치기반 서비스 이용 동의' }));
    expect(next).toBeEnabled();
    fireEvent.click(next);
    expect(await screen.findByText('다음 화면')).toBeInTheDocument();
  });

  // 전체 동의를 한 번 누르면 화면의 체크박스(전체 동의 포함)가 모두 켜지고, 다시 누르면 하나도 남지 않는지 본다
  it('전체 동의하기는 5개를 모두 켜고 끈다', async () => {
    renderTerms();
    const all = await screen.findByRole('checkbox', { name: '전체 동의하기' });
    fireEvent.click(all);
    expect(screen.getAllByRole('checkbox').every((c) => c.checked)).toBe(true);
    fireEvent.click(all);
    expect(screen.getAllByRole('checkbox').some((c) => c.checked)).toBe(false);
  });
});
