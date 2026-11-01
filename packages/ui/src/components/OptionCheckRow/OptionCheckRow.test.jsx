// OptionCheckRow 테스트: checkbox 로 드러나고 이름의 줄바꿈이 공백이 되며, disabled·onToggle 이 동작하는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import OptionCheckRow from './OptionCheckRow.jsx';

describe('OptionCheckRow', () => {
  it('선택 표시와 누르기', () => {
    const onToggle = vi.fn();
    const { rerender } = render(<OptionCheckRow icon={null} label={'진열대\n먼지 제거'} selected={false} onToggle={onToggle} />);
    const box = screen.getByRole('checkbox', { name: '진열대 먼지 제거' });
    expect(box).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(box);
    expect(onToggle).toHaveBeenCalledTimes(1);
    rerender(<OptionCheckRow icon={null} label="분리배출" selected={false} disabled onToggle={onToggle} />);
    expect(screen.getByRole('checkbox', { name: '분리배출' })).toBeDisabled();
  });
});
