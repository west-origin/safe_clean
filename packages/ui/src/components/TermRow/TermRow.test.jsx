// TermRow 테스트: 체크박스를 누르면 onChange(true), "보기>" 버튼을 누르면 onView 가 따로 불리는지,
// 보기 버튼 이름이 "<약관 문구> 보기" 인지, CSS 에 터치 영역 토큰이 쓰였는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import TermRow from './TermRow.jsx';

// 테스트용 가짜 브라우저(jsdom)에서는 CSS 값을 확인하기 어려워, CSS 파일 내용을 문자열로 읽어 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'TermRow.module.css'), 'utf8');

describe('TermRow', () => {
  it('체크박스와 "보기>" 버튼이 각각 동작한다', () => {
    const onChange = vi.fn();
    const onView = vi.fn();
    render(<TermRow label="(필수) 서비스 이용약관" checked={false} onChange={onChange} onView={onView} />);
    fireEvent.click(screen.getByRole('checkbox', { name: '(필수) 서비스 이용약관' }));
    expect(onChange).toHaveBeenCalledWith(true);
    const view = screen.getByRole('button', { name: '(필수) 서비스 이용약관 보기' });
    expect(view).toHaveTextContent('보기>');
    fireEvent.click(view);
    expect(onView).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });
});
