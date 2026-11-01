// AddressOption 테스트: 라디오로 드러나고, 줄을 누르면 onSelect, '수정' 은 onEdit 만 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AddressOption from './AddressOption.jsx';

describe('AddressOption', () => {
  it('라디오 선택과 수정 버튼이 따로 동작한다', () => {
    const onSelect = vi.fn();
    const onEdit = vi.fn();
    render(<AddressOption name="a" label="집" address="서울 강남구 1" selected={false} onSelect={onSelect} onEdit={onEdit} />);
    const radio = screen.getByRole('radio', { name: /집/ });
    expect(radio).not.toBeChecked();
    fireEvent.click(screen.getByRole('button', { name: '집 주소 수정' }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(onSelect).not.toHaveBeenCalled();
    fireEvent.click(radio);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });
});
