import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import Skeleton from './Skeleton.jsx';

// Skeleton 테스트: 숫자 크기가 px 로, radius 이름이 토큰 변수로 style 에 들어가고, aria-hidden 으로 숨겨지는지.
// 글자·역할이 없는 요소라 getByRole 대신 container.firstChild 로 직접 꺼낸다.

describe('Skeleton', () => {
  it('크기와 radius 토큰을 style 로 받고 보조기기에서는 숨긴다', () => {
    const { container } = render(<Skeleton width={120} height={20} radius="pill" />);
    const el = container.firstChild;
    expect(el).toHaveAttribute('aria-hidden', 'true');
    expect(el.style.width).toBe('120px');
    expect(el.style.height).toBe('20px');
    expect(el.style.borderRadius).toBe('var(--radius-pill)');
  });
});
