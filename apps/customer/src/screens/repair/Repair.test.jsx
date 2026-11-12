// 고객 앱 수리 탭 화면(Repair.jsx) 테스트. 히어로·카테고리 격자가 빠지고 견적 안내가 보이는지 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import Repair from './Repair.jsx';

// 목: api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — GET /customer/repair 의 히어로 문구가 여기서 온다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Repair 화면', () => {
  // 예약 초안이 저장되는 sessionStorage 를 테스트마다 비운다 — 이 화면은 지금 초안을 쓰지 않지만 다른 예약 화면 테스트와 같은 준비로 둔다
  beforeEach(() => sessionStorage.clear());

  it('히어로와 카테고리 격자는 없다 (2026-11-08)', () => {
    render(<MemoryRouter><Repair /></MemoryRouter>);
    expect(screen.queryByRole('region', { name: '수리 서비스 소개' })).toBeNull();
    expect(screen.queryByRole('button', { name: /배관·누수/ })).toBeNull();
  });

  it('기본 청소 화면과 같은 틀: 제목·수리 종류 라디오, 고른 종류의 견적 안내만 보인다', async () => {
    render(<MemoryRouter><Repair /></MemoryRouter>);
    expect(screen.getByRole('heading', { level: 1, name: '수리' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /대리석 코팅/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('420,000')).toBeInTheDocument();
    expect(screen.queryByText('300,000')).toBeNull();
    fireEvent.click(screen.getByRole('radio', { name: /대리석 크랙 수리/ }));
    expect(screen.getByText('300,000')).toBeInTheDocument();
  });
});
