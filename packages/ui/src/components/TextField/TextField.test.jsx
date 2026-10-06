// TextField 테스트: Figma 상태(Default·Filled·Error·Disabled)와 코드에서 더한 hint·success 가 DOM 속성·문구로 드러나는지,
// id·나머지 props 가 <input> 에 전달되는지, CSS 가 Focused·Error 규칙과 터치 영역 토큰을 쓰고 hex 색이 없는지 확인한다.
// getByLabelText 는 <label> 문구로 연결된 입력칸을 찾는다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import TextField from './TextField.jsx';

// 테스트용 가짜 브라우저(jsdom)에서는 CSS 값을 확인하기 어려워, CSS 파일 내용을 문자열로 읽어 정규식으로 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'TextField.module.css'), 'utf8');

describe('TextField', () => {
  it('라벨이 input 과 연결되고 placeholder 가 보인다 (Default)', () => {
    render(<TextField label="라벨" placeholder="내용을 입력해주세요" />);
    const input = screen.getByLabelText('라벨');
    expect(input).toHaveAttribute('placeholder', '내용을 입력해주세요');
    expect(input).not.toHaveAttribute('aria-invalid');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('값을 입력하면 onChange 가 호출된다 (Filled)', () => {
    const onChange = vi.fn();
    render(<TextField label="라벨" value="입력된 내용" onChange={onChange} />);
    const input = screen.getByLabelText('라벨');
    expect(input).toHaveValue('입력된 내용');
    fireEvent.change(input, { target: { value: '수정' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('Error 는 색만이 아니라 헬퍼 텍스트 + aria-invalid + aria-describedby 로 드러난다', () => {
    render(<TextField label="라벨" error="올바른 값을 입력해주세요" />);
    const input = screen.getByLabelText('라벨');
    const helper = screen.getByRole('alert');
    expect(helper).toHaveTextContent('올바른 값을 입력해주세요');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-describedby', helper.id);
  });

  it('Disabled 는 input 이 비활성화되고 오류 표시가 사라진다', () => {
    render(<TextField label="라벨" disabled error="무시됨" />);
    const input = screen.getByLabelText('라벨');
    expect(input).toBeDisabled();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('id 를 주면 그대로 쓰고, 나머지 props 는 input 에 전달된다', () => {
    render(<TextField label="휴대폰" id="phone" type="tel" inputMode="numeric" name="phone" />);
    const input = screen.getByLabelText('휴대폰');
    expect(input).toHaveAttribute('id', 'phone');
    expect(input).toHaveAttribute('type', 'tel');
    expect(input).toHaveAttribute('inputmode', 'numeric');
    expect(input).toHaveAttribute('name', 'phone');
  });

  it('hint 는 입력 전 안내로 보이고, error·success 가 있으면 그쪽이 이긴다', () => {
    // 같은 필드를 rerender 로 props 만 바꿔 다시 그리며 hint → success → error 순서로 안내가 바뀌는지 본다
    const { rerender } = render(<TextField label="비밀번호" hint="8자 이상 입력해주세요" />);
    const input = screen.getByLabelText('비밀번호');
    expect(screen.getByText('8자 이상 입력해주세요')).toBeInTheDocument();
    expect(input).toHaveAttribute('aria-describedby'); // 화면 낭독기도 규칙을 읽는다

    rerender(<TextField label="비밀번호" hint="8자 이상 입력해주세요" success="사용가능한 비밀번호입니다!" />);
    expect(screen.queryByText('8자 이상 입력해주세요')).toBeNull();

    rerender(<TextField label="비밀번호" hint="8자 이상 입력해주세요" error="올바른 값을 입력해주세요" />);
    expect(screen.queryByText('8자 이상 입력해주세요')).toBeNull();
    expect(screen.getByRole('alert')).toHaveTextContent('올바른 값을 입력해주세요');
  });

  it('CSS 가 Focused=:focus-within, Error=status/live, 터치 타겟 토큰을 쓰고 하드코딩 색이 없다', () => {
    expect(css).toMatch(/\.inputBox:focus-within \{[^}]*border-color: var\(--color-brand-primary\)/);
    expect(css).toMatch(/\.error \.inputBox[^{]*\{[^}]*border-color: var\(--color-status-live\)/);
    expect(css).toContain('min-height: var(--touch-target)');
    expect(css).not.toMatch(/#[0-9a-f]{3,8}\b/i);
  });
});
