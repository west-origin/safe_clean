// ProgressBar 검증: 이름 붙은 progressbar 로 그려지고, aria-valuenow 와 채움 폭(%)이 value/max 와 맞는지.
// 채움 폭은 progressbar 의 첫 자식(채움 div)의 인라인 style 로 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ProgressBar from './ProgressBar.jsx';

describe('ProgressBar', () => {
  it('value/max 가 progressbar 값과 채움 폭으로 드러난다', () => {
    render(<ProgressBar value={4} max={9} label="청소 체크리스트" />);
    const bar = screen.getByRole('progressbar', { name: '청소 체크리스트' });
    expect(bar).toHaveAttribute('aria-valuenow', '4');
    expect(bar.firstChild.style.width).toBe(`${(4 / 9) * 100}%`);
  });
});
