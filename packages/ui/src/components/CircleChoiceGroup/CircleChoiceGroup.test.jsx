// CircleChoiceGroup 테스트: 처음엔 아무것도 안 고른 라디오 묶음이고, 누르면 onChange(key), 고른 것은 aria-checked.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import CircleChoiceGroup from './CircleChoiceGroup.jsx';

const OPTIONS = [{ key: 'yes', label: '있음', icon: null }, { key: 'no', label: '없음', icon: null }];

describe('CircleChoiceGroup', () => {
  it('선택 없음 → 고르기', () => {
    const onChange = vi.fn();
    const { rerender } = render(<CircleChoiceGroup label="영유아" options={OPTIONS} onChange={onChange} />);
    expect(screen.getByRole('radiogroup', { name: '영유아' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true);
    fireEvent.click(screen.getByRole('radio', { name: '없음' }));
    expect(onChange).toHaveBeenCalledWith('no');
    rerender(<CircleChoiceGroup label="영유아" options={OPTIONS} value="no" onChange={onChange} />);
    expect(screen.getByRole('radio', { name: '없음' })).toHaveAttribute('aria-checked', 'true');
  });
});
