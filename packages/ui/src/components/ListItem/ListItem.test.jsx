import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ListItem from './ListItem.jsx';

// ListItem 테스트: onClick 을 주면 버튼으로 그려져 클릭이 전달되고,
// href 를 주면 같은 주소의 링크로 그려지는지 확인한다.
describe('ListItem', () => {
  it('onClick 이면 button, href 면 link 로 렌더된다', () => {
    const onClick = vi.fn();
    const { rerender } = render(<ListItem label="매니저 정보" onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: '매니저 정보' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    rerender(<ListItem label="이용약관" href="https://example.com/terms" />);
    expect(screen.getByRole('link', { name: '이용약관' })).toHaveAttribute('href', 'https://example.com/terms');
  });
});
