// SearchField 테스트: 라벨이 input 과 이어져 getByLabelText(라벨 글자로 입력칸 찾기)로 찾히는지, 입력하면 onChange,
// disabled 가 input 속성으로 드러나는지, CSS 에 터치 영역 변수(--touch-target)가 쓰였는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import SearchField from './SearchField.jsx';

// CSS Module 파일을 글자 그대로 읽어 둔다 (터치 영역 변수를 썼는지 글자로 확인)
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'SearchField.module.css'), 'utf8');

describe('SearchField', () => {
  it('라벨이 input 과 연결되고 입력하면 onChange 가 호출된다', () => {
    const onChange = vi.fn();
    render(<SearchField label="도로명주소" placeholder="도로명주소를 입력해주세요." value="" onChange={onChange} />);
    const input = screen.getByLabelText('도로명주소');
    expect(input).toHaveAttribute('placeholder', '도로명주소를 입력해주세요.');
    fireEvent.change(input, { target: { value: '테헤란로' } });
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });

  it('disabled 면 input 이 비활성화된다', () => {
    render(<SearchField label="도로명주소" disabled />);
    expect(screen.getByLabelText('도로명주소')).toBeDisabled();
  });
});
