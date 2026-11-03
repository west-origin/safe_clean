// BrandWordmark 테스트: 그림 로고지만 보조기기에는 '안심클린' 하나로 읽힌다. withMark 면 아이콘 svg 가 하나 더, 글자는 칸 폭에 딱 맞춘다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import BrandWordmark from './BrandWordmark.jsx';

describe('BrandWordmark', () => {
  it('이름이 안심클린인 그림 (아이콘 없음)', () => {
    render(<BrandWordmark width={120} />);
    const logo = screen.getByRole('img', { name: '안심클린' });
    expect(logo.querySelectorAll('svg')).toHaveLength(1);
    expect(logo.querySelector('svg')).toHaveAttribute('width', '120');
    expect(logo.querySelector('text')).toHaveAttribute('lengthAdjust', 'spacingAndGlyphs');
  });

  it('withMark 면 왼쪽에 아이콘', () => {
    render(<BrandWordmark withMark />);
    const logo = screen.getByRole('img', { name: '안심클린' });
    expect(logo.querySelectorAll('svg')).toHaveLength(2);
  });
});
