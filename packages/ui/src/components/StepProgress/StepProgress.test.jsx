// StepProgress 테스트: Step 1~8 을 차례로 그려 보며 aria-valuenow 가 단계 번호와 같은지,
// 점이 8개이고 그중 active 클래스가 붙은 점이 정확히 하나인지 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StepProgress from './StepProgress.jsx';

describe('StepProgress', () => {
  it('Step 1~8 이 progressbar 값으로 드러나고 활성 점은 하나다', () => {
    for (let step = 1; step <= 8; step++) {
      const { container, unmount } = render(<StepProgress step={step} />);
      const bar = screen.getByRole('progressbar');
      expect(bar).toHaveAttribute('aria-valuenow', String(step));
      expect(container.querySelectorAll('span').length).toBe(8);
      expect(container.querySelectorAll('[class*="active"]').length).toBe(1);
      // 다음 반복에서 progressbar 가 두 개가 되지 않도록 이번 렌더를 치운다
      unmount();
    }
  });
});
