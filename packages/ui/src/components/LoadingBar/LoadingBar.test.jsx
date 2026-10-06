import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import LoadingBar from './LoadingBar.jsx';

// LoadingBar 테스트: active=false 면 progressbar 가 없고, active 면 기본 라벨로 나타나는지 본다.
describe('LoadingBar', () => {
  it('active 일 때만 progressbar 를 그린다', () => {
    const { rerender } = render(<LoadingBar active={false} />);
    expect(screen.queryByRole('progressbar')).toBeNull();
    rerender(<LoadingBar active />);
    expect(screen.getByRole('progressbar', { name: '화면을 불러오는 중' })).toBeInTheDocument();
  });
});
