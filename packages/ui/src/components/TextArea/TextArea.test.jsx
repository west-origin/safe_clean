// TextArea 테스트: ariaLabel 이 접근성 이름("요청사항")으로 잡히고 placeholder 가 붙는지,
// 입력(change 이벤트)하면 onChange 가 불리는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TextArea from './TextArea.jsx';

describe('TextArea', () => {
  it('접근성 라벨·placeholder 가 있고 입력하면 onChange', () => {
    const onChange = vi.fn();
    render(<TextArea ariaLabel="요청사항" placeholder="여기에 작성해주세요" value="" onChange={onChange} />);
    const area = screen.getByRole('textbox', { name: '요청사항' });
    expect(area).toHaveAttribute('placeholder', '여기에 작성해주세요');
    fireEvent.change(area, { target: { value: '창문' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });
});
