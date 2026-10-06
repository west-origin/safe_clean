// FaqItem 테스트: 질문 버튼의 aria-expanded 가 open 을 따르고, 닫혀 있으면 답변이 DOM 에 없으며,
// 누르면 onToggle 을 부르는지 본다. 열고 닫기는 부모 몫이라 rerender 로 open 을 바꿔 준다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import FaqItem from './FaqItem.jsx';

describe('FaqItem', () => {
  it('aria-expanded 로 상태가 드러나고 열렸을 때만 답변이 보인다', () => {
    const onToggle = vi.fn();
    const { rerender } = render(<FaqItem question="Q" answer="A" open={false} onToggle={onToggle} />);
    expect(screen.getByRole('button', { name: 'Q' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('A')).toBeNull();
    fireEvent.click(screen.getByRole('button'));
    expect(onToggle).toHaveBeenCalled();
    rerender(<FaqItem question="Q" answer="A" open onToggle={onToggle} />);
    expect(screen.getByText('A')).toBeInTheDocument();
  });
});
