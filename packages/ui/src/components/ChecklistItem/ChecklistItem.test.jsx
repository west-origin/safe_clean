// ChecklistItem 테스트: 체크 상태가 checkbox 역할로 드러나고 클릭 시 onChange 에 새 값이 가는지,
// readOnly 가 체크박스 없이 목록 항목(이름에 "(포함)")으로 렌더되는지, disabled 속성을 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ChecklistItem from './ChecklistItem.jsx';

describe('ChecklistItem', () => {
  it('Checked/Unchecked 가 checkbox 상태로 드러나고 클릭하면 onChange(반대값) 를 부른다', () => {
    // 제어 컴포넌트라 클릭만으로는 체크가 바뀌지 않는다. rerender 로 부모가 checked 를 바꿔 내려 준 상황을 흉내 낸다.
    const onChange = vi.fn();
    const { rerender } = render(<ChecklistItem label="바닥 청소" checked={false} onChange={onChange} />);
    const box = screen.getByRole('checkbox', { name: '바닥 청소' });
    expect(box).not.toBeChecked();
    fireEvent.click(box);
    expect(onChange).toHaveBeenCalledWith(true);
    rerender(<ChecklistItem label="바닥 청소" checked onChange={onChange} />);
    expect(screen.getByRole('checkbox')).toBeChecked();
  });

  it('readOnly 면 체크박스 없이 목록 항목으로 렌더된다', () => {
    render(<ChecklistItem label="거실" checked readOnly />);
    expect(screen.queryByRole('checkbox')).toBeNull();
    expect(screen.getByRole('listitem', { name: '거실 (포함)' })).toBeInTheDocument();
  });

  it('disabled 면 체크박스가 비활성화된다', () => {
    render(<ChecklistItem label="바닥 청소" checked={false} disabled />);
    expect(screen.getByRole('checkbox')).toBeDisabled();
  });
});
