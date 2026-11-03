// BrandWordmark 테스트: 그림 로고지만 보조기기에는 '안심클린' 이름으로 읽힌다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import BrandWordmark from './BrandWordmark.jsx';

describe('BrandWordmark', () => {
  it('이름이 안심클린인 그림', () => {
    render(<BrandWordmark width={120} />);
    expect(screen.getByRole('img', { name: '안심클린' })).toHaveAttribute('width', '120');
  });
});
