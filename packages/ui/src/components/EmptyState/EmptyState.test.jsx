// EmptyState 테스트: role="status" 영역에 제목이 들어가고, 원 안에 SVG 아이콘이 그려지는지 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import EmptyState from './EmptyState.jsx';

describe('EmptyState', () => {
  it('제목·설명·아이콘을 렌더한다', () => {
    const { container } = render(<EmptyState icon="call" title="대기 중인 청소 요청이 없어요" description="새로운 요청이 들어오면 알림으로 알려드릴게요." />);
    expect(screen.getByRole('status')).toHaveTextContent('대기 중인 청소 요청이 없어요');
    // 아이콘은 aria-hidden 이라 역할로 못 찾아서, 렌더된 DOM(container)에서 svg 태그를 직접 찾는다.
    expect(container.querySelector('svg')).not.toBeNull();
  });
});
