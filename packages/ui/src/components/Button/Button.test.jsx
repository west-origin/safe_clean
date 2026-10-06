// Button 검증: 모든 variant 렌더, primary 만 chevron, disabled·loading 때 클릭 막힘, CSS 가 토큰을 쓰는지.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Button from './Button.jsx';

// CSS Module 파일을 글자 그대로 읽어 둔다 — 아래 CSS 테스트가 토큰·:active 규칙을 문자열로 확인한다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'Button.module.css'), 'utf8');

describe('Button', () => {
  it('다섯 variant 가 모두 button 으로 렌더된다', () => {
    for (const variant of ['primary', 'secondary', 'destructive', 'outline', 'outlineGray']) {
      const { unmount } = render(<Button variant={variant}>라벨</Button>);
      const btn = screen.getByRole('button', { name: '라벨' });
      expect(btn).toHaveAttribute('type', 'button');
      expect(btn.className).toContain(variant);
      unmount();
    }
  });

  // chevron 은 svg 아이콘이라 svg 가 있는지로 본다. rerender 는 같은 자리에 props 만 바꿔 다시 그린다.
  it('primary 만 기본으로 chevron 을 보여준다', () => {
    const { container, rerender } = render(<Button variant="primary">다음</Button>);
    expect(container.querySelector('svg')).not.toBeNull();
    rerender(<Button variant="secondary">거절</Button>);
    expect(container.querySelector('svg')).toBeNull();
    rerender(<Button variant="destructive">취소하기</Button>);
    expect(container.querySelector('svg')).toBeNull();
  });

  it('disabled 면 disabled 속성이 붙고 chevron 이 숨겨진다', () => {
    const onClick = vi.fn();
    const { container } = render(
      <Button variant="primary" disabled onClick={onClick}>
        다음
      </Button>
    );
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    expect(container.querySelector('svg')).toBeNull();
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('loading 이면 aria-busy, 스피너, "처리 중" 라벨로 바뀌고 chevron 이 없고 클릭이 막힌다', () => {
    const onClick = vi.fn();
    render(
      <Button variant="primary" loading onClick={onClick}>
        다음
      </Button>
    );
    const btn = screen.getByRole('button');
    expect(btn).toHaveAttribute('aria-busy', 'true');
    // 스피너는 Spinner 아이콘의 role="img" + aria-label "로딩 중" 으로 찾는다
    expect(screen.getByRole('img', { name: '로딩 중' })).toBeInTheDocument();
    expect(screen.getByText('처리 중')).toBeInTheDocument();
    expect(screen.queryByText('다음')).toBeNull();
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('클릭하면 onClick 이 호출된다', () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>확인</Button>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('CSS 가 터치 타겟 토큰과 :active(Pressed) 를 쓰고 하드코딩 색이 없다', () => {
    expect(css).toContain('min-height: var(--touch-target)');
    expect(css).toMatch(/\.primary:active[^{]*\{\s*background: var\(--color-brand-primary-pressed\)/);
    expect(css).toMatch(/\.secondary:active[^{]*\{\s*background: var\(--color-surface-pressed\)/);
    expect(css).toMatch(/\.destructive:active[^{]*\{\s*background: var\(--color-status-live-subtle\)/);
    // 16진수 색 표기가 없어야 한다. 단 주석 안에 있는 것(뒤에 주석 닫는 기호가 오는 것)은 빼고 찾는다
    expect(css).not.toMatch(/#[0-9a-f]{3,8}\b(?![^/]*\*\/)/i);
  });
});
