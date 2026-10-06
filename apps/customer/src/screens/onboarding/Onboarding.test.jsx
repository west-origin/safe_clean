// 온보딩 화면(Onboarding.jsx)과 무작위 장 고르기(pickSlide.js) 테스트.
// 슬라이드가 한 장(버튼 하나)만 그려지고 누르면 가입 화면으로 가는지, 무작위 번호가 범위 안인지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Onboarding from './Onboarding.jsx';
import { pickSlide } from './pickSlide.js';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 (이 화면 자체는 서버를 부르지 않는다)
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Onboarding — 접속마다 한 장 무작위 (사용자 결정 2026-09-21)', () => {
  // 준비: slideIndex={1} 로 둘째 장을 고정한다. 확인: 버튼이 하나뿐이고 그 이름이 둘째 장 문구, 누르면 /signup
  it('슬라이드 한 장만 그리고 탭하면 가입 화면', async () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Onboarding slideIndex={1} />} />
          <Route path="/signup" element={<p>가입 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveAccessibleName('보이지 않는 곳까지 안심하는 깨끗함. 탭하면 시작');
    fireEvent.click(buttons[0]);
    expect(await screen.findByText('가입 화면')).toBeInTheDocument();
  });

  // Math.random 을 고정하지 않고 20번씩 뽑아 0 이상 3 미만인지만 본다
  it('무작위 인덱스는 0~2 안', () => {
    for (let i = 0; i < 20; i += 1) expect(pickSlide(3)).toBeGreaterThanOrEqual(0);
    for (let i = 0; i < 20; i += 1) expect(pickSlide(3)).toBeLessThan(3);
  });
});
