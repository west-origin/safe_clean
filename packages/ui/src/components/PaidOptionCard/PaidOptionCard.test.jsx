// PaidOptionCard 테스트: 이름+가격이 checkbox 이름이 되고, 고르면 aria-checked 가 바뀌는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import PaidOptionCard from './PaidOptionCard.jsx';

describe('PaidOptionCard', () => {
  it('이름·가격과 선택', () => {
    const onToggle = vi.fn();
    const { rerender } = render(<PaidOptionCard icon={null} label={'주방\n기름때 청소'} price="+50,000원" selected={false} onToggle={onToggle} />);
    fireEvent.click(screen.getByRole('checkbox', { name: '주방 기름때 청소 +50,000원' }));
    expect(onToggle).toHaveBeenCalledTimes(1);
    rerender(<PaidOptionCard icon={null} label="주방 기름때 청소" price="+50,000원" selected onToggle={onToggle} />);
    expect(screen.getByRole('checkbox')).toHaveAttribute('aria-checked', 'true');
  });
});
