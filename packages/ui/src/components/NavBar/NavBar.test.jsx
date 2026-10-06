// NavBar 검증: 제목이 heading 으로 보이는지, 뒤로가기 버튼이 onBack 을 부르는지, onBack 이 없으면 버튼이 없는지.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import NavBar from './NavBar.jsx';

describe('NavBar', () => {
  it('제목을 heading 으로 렌더하고 뒤로가기 버튼이 onBack 을 호출한다', () => {
    const onBack = vi.fn();
    render(<NavBar title="화면 제목" onBack={onBack} />);
    expect(screen.getByRole('heading', { name: '화면 제목' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '뒤로가기' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('onBack 이 없으면 뒤로가기 버튼이 없다', () => {
    render(<NavBar title="홈" />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
