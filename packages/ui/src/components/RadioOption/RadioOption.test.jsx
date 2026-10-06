// RadioOption 테스트: 보조기기에 라디오(role=radio, aria-checked)로 드러나는지와 누르면 onSelect 를 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RadioOption from './RadioOption.jsx';

describe('RadioOption', () => {
  // 선택 안 됨으로 그려 클릭 → onSelect 한 번. 같은 컴포넌트를 selected 로 다시 그려(rerender) aria-checked 가 바뀌는지 본다.
  it('Selected/Default 가 role=radio + aria-checked 로 드러나고 클릭하면 onSelect', () => {
    const onSelect = vi.fn();
    const { rerender } = render(<RadioOption label="옵션" selected={false} onSelect={onSelect} />);
    const radio = screen.getByRole('radio', { name: '옵션' });
    expect(radio).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(radio);
    expect(onSelect).toHaveBeenCalledTimes(1);
    rerender(<RadioOption label="옵션" selected onSelect={onSelect} />);
    expect(screen.getByRole('radio')).toHaveAttribute('aria-checked', 'true');
  });
});
