// ToggleChip 테스트: 선택된 칩(selected)이 aria-pressed="true" 로 드러나고, 누르면 반대값(false)으로 onToggle 을 부르는지 본다.
import { test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ToggleChip from './ToggleChip.jsx';

test('aria-pressed 로 선택 상태를 드러내고 탭하면 반대 값으로 onToggle', () => {
  const onToggle = vi.fn();
  render(<ToggleChip label="주차 가능해요" selected onToggle={onToggle} />);
  const chip = screen.getByRole('button', { name: '주차 가능해요' });
  expect(chip).toHaveAttribute('aria-pressed', 'true');
  fireEvent.click(chip);
  expect(onToggle).toHaveBeenCalledWith(false);
});
