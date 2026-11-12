// OtpCodeField: 입력 하나 + 칸 6개. 숫자만 받고, 오류는 문구·aria-invalid 로 드러나고, 터치 영역은 --touch-target
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import OtpCodeField from './OtpCodeField.jsx';

describe('OtpCodeField', () => {
  it('숫자만 6자리로 잘라 문자열로 넘긴다', () => {
    const onChange = vi.fn();
    render(<OtpCodeField label="인증번호" value="" onChange={onChange} />);
    const input = screen.getByRole('textbox', { name: '인증번호' });
    expect(input).toHaveAttribute('autocomplete', 'one-time-code');
    expect(input).toHaveAttribute('inputmode', 'numeric');
    fireEvent.change(input, { target: { value: '12-34 5678' } });
    expect(onChange).toHaveBeenCalledWith('123456');
  });

  it('값을 칸에 나눠 보이고 채운 칸 상태를 표시한다', () => {
    const { container } = render(<OtpCodeField label="인증번호" value="12" />);
    const boxes = container.querySelectorAll('[data-state]');
    expect(boxes).toHaveLength(6);
    expect(boxes[0]).toHaveTextContent('1');
    expect(boxes[0]).toHaveAttribute('data-state', 'filled');
    expect(boxes[2]).toHaveAttribute('data-state', 'empty');
  });

  it('오류는 문구와 aria-invalid 로, disabled 는 속성으로', () => {
    const { rerender } = render(<OtpCodeField label="인증번호" value="000000" error="인증번호가 맞지 않아요" />);
    const input = screen.getByRole('textbox', { name: '인증번호' });
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('인증번호가 맞지 않아요');
    rerender(<OtpCodeField label="인증번호" value="" disabled />);
    expect(screen.getByRole('textbox', { name: '인증번호' })).toBeDisabled();
  });

  it('남은 시간을 칸 아래에 보인다', () => {
    render(<OtpCodeField label="인증번호" value="" timer="02:58" />);
    expect(screen.getByText('02:58')).toBeInTheDocument();
  });

  it('입력 영역은 터치 타겟 높이를 쓴다', () => {
    const css = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'OtpCodeField.module.css'), 'utf8');
    expect(css).toContain('var(--touch-target)');
  });
});
