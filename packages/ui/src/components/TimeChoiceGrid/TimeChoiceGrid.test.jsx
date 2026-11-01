// TimeChoiceGrid 테스트: 묶음별 시간이 라디오로, 앞 0 을 떼고, 꺼진 시간은 disabled
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TimeChoiceGrid from './TimeChoiceGrid.jsx';

describe('TimeChoiceGrid', () => {
  it('시간 고르기', () => {
    const onChange = vi.fn();
    render(<TimeChoiceGrid groups={[{ label: '오전', times: ['08:00', '09:00'] }, { label: '오후', times: ['14:00'] }]} value="09:00" onChange={onChange} isDisabled={(t) => t === '08:00'} />);
    expect(screen.getByRole('radio', { name: '8:00' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: '9:00' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('radio', { name: '14:00' }));
    expect(onChange).toHaveBeenCalledWith('14:00');
  });
});
