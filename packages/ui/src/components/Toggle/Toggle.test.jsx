// Toggle 테스트: role=switch 와 aria-checked 로 켜짐/꺼짐이 드러나고, 누르면 반대값으로 onChange 를 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import Toggle from './Toggle.jsx';

describe('Toggle', () => {
  it('switch 로 On/Off 가 드러나고 누르면 반대값을 넘긴다', () => {
    const onChange = vi.fn();
    const { rerender } = render(<Toggle checked={false} onChange={onChange} label="푸시 알림" />);
    const sw = screen.getByRole('switch', { name: '푸시 알림' });
    expect(sw).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(sw);
    expect(onChange).toHaveBeenCalledWith(true);
    // Toggle 은 스스로 켜지지 않는다. rerender 로 부모가 checked 를 true 로 바꿔 내려준 경우를 흉내 낸다
    rerender(<Toggle checked onChange={onChange} label="푸시 알림" />);
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
  });
});
