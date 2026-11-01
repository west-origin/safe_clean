// DurationStepper 테스트: min/max 에서 버튼이 꺼지고 step 만큼 바뀌는지
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import DurationStepper from './DurationStepper.jsx';

describe('DurationStepper', () => {
  it('2~8시간, 1시간씩', () => {
    const onChange = vi.fn();
    const { rerender } = render(<DurationStepper hours={2} min={2} max={8} onChange={onChange} priceLabel="41,600원" note="표준시간은 2시간 입니다." />);
    expect(screen.getByText('2시간 00분')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '시간 줄이기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '시간 늘리기' }));
    expect(onChange).toHaveBeenCalledWith(3);
    rerender(<DurationStepper hours={8} min={2} max={8} onChange={onChange} />);
    expect(screen.getByRole('button', { name: '시간 늘리기' })).toBeDisabled();
  });
});
