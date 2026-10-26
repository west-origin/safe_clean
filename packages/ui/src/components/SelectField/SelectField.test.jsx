import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SelectField from './SelectField.jsx';

// SelectField 테스트: 값 유무에 따라 버튼에 보이는 글자(= 버튼 이름)가 바뀌는지, 누르면 onClick 이 불리는지.

describe('SelectField', () => {
  it('선행 입력이 끝나지 않아 disabled이면 이동 이벤트를 보내지 않는다', () => {
    const onClick = vi.fn();
    render(<SelectField placeholder="옵션 선택" disabled onClick={onClick} />);
    const button = screen.getByRole('button', { name: '옵션 선택' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
  it('값이 없으면 placeholder, 있으면 값을 보여주고 누르면 onClick', () => {
    const onClick = vi.fn();
    const { rerender } = render(<SelectField placeholder="날짜/시간을 선택해주세요" icon="calendar" onClick={onClick} />);
    const btn = screen.getByRole('button', { name: '날짜/시간을 선택해주세요' });
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
    // rerender: 같은 컴포넌트를 value 만 넣어 다시 그린다 → 이제 버튼 이름이 값 문구여야 한다
    rerender(<SelectField placeholder="날짜/시간을 선택해주세요" value="9월 16일 (화) 11:00" icon="calendar" />);
    expect(screen.getByRole('button', { name: '9월 16일 (화) 11:00' })).toBeInTheDocument();
  });
});
