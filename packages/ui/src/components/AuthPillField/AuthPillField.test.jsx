// AuthPillField 테스트: 라벨로 입력칸을 찾을 수 있고(숨은 라벨), 입력하면 onChange, 나머지 props 는 input 으로 간다. 터치 높이 토큰을 쓴다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import AuthPillField from './AuthPillField.jsx';

const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'AuthPillField.module.css'), 'utf8');

describe('AuthPillField', () => {
  it('숨은 라벨 + placeholder, 입력과 props 전달', () => {
    const onChange = vi.fn();
    render(<AuthPillField icon="lock" label="비밀번호 입력" type="password" value="" onChange={onChange} />);
    const input = screen.getByLabelText('비밀번호 입력');
    expect(input).toHaveAttribute('placeholder', '비밀번호 입력');
    expect(input).toHaveAttribute('type', 'password');
    expect(screen.getByText('비밀번호 입력')).toHaveClass('sr-only');
    fireEvent.change(input, { target: { value: 'abc' } });
    expect(onChange).toHaveBeenCalled();
  });

  it('터치 높이 토큰을 쓴다', () => {
    expect(css).toMatch(/min-height:\s*var\(--touch-target\)/);
  });
});
