// ChoiceCardGroup 테스트: 처음엔 아무것도 안 고른 라디오 묶음이고, 누르면 onChange(key), 고른 것은 aria-checked. 아이콘 없는 선택지도 된다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ChoiceCardGroup from './ChoiceCardGroup.jsx';

const OPTIONS = [{ key: 'yes', label: '있음', icon: <svg data-testid="icon" /> }, { key: 'no', label: '없음' }];

describe('ChoiceCardGroup', () => {
  it('선택 없음 → 고르기', () => {
    const onChange = vi.fn();
    const { rerender } = render(<ChoiceCardGroup label="영유아" options={OPTIONS} onChange={onChange} />);
    expect(screen.getByRole('radiogroup', { name: '영유아' })).toBeInTheDocument();
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true);
    expect(screen.getAllByTestId('icon')).toHaveLength(1);
    fireEvent.click(screen.getByRole('radio', { name: '없음' }));
    expect(onChange).toHaveBeenCalledWith('no');
    rerender(<ChoiceCardGroup label="영유아" options={OPTIONS} value="no" onChange={onChange} />);
    expect(screen.getByRole('radio', { name: '없음' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: '없음' })).toHaveAttribute('data-state', 'selected');
  });
});
