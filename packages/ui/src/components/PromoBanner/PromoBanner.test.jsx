// PromoBanner 검증: 두 variant 모두 "정기청소 안내" 영역(region)으로 그려지고 혜택 알약이 3개인지,
// benefits 모양에 Figma 자리표시 문구("…이미지 필요")가 새어 나오지 않는지.
// 이름(aria-label) 붙은 <section> 은 role "region" 으로 찾을 수 있다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PromoBanner from './PromoBanner.jsx';

describe('PromoBanner', () => {
  it('recurring: 문구·혜택 3개', () => {
    render(<PromoBanner />);
    expect(screen.getByRole('region', { name: '정기청소 안내' })).toHaveTextContent('늘 깨끗한 우리 집');
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('benefits: 개발용 자리표시 문구 대신 혜택 알약 3개만 (사용성 검토 2026-09-22)', () => {
    render(<PromoBanner variant="benefits" />);
    const banner = screen.getByRole('region', { name: '정기청소 안내' });
    expect(banner).toHaveTextContent('정기청소');
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(banner).not.toHaveTextContent('이미지 필요'); // 사용자에게 보이면 안 되는 문구
  });
});
