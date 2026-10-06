import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StepDots from './StepDots.jsx';

// StepDots 테스트: progressbar 역할·이름·aria-valuenow 가 맞는지, step 1~4 에서 카운터가 두 자리로 나오는지.
// 카운터는 aria-hidden 이지만 toHaveTextContent 는 DOM 글자를 그대로 보므로 확인할 수 있다.

describe('StepDots', () => {
  it('progressbar 로 현재 단계를 드러내고 카운터를 두 자리로 표시한다', () => {
    render(<StepDots step={2} total={4} />);
    const bar = screen.getByRole('progressbar', { name: '4단계 중 2단계' });
    expect(bar).toHaveAttribute('aria-valuenow', '2');
    expect(bar).toHaveTextContent('02/04');
  });

  it('step 1~4 모두 렌더된다', () => {
    for (const step of [1, 2, 3, 4]) {
      const { unmount } = render(<StepDots step={step} />);
      expect(screen.getByRole('progressbar')).toHaveTextContent(`0${step}/04`);
      unmount();
    }
  });
});
