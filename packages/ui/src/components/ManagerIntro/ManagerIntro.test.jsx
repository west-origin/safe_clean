// ManagerIntro 테스트: 안내 한 줄과 링크 버튼, 장식 그림은 alt=""
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ManagerIntro from './ManagerIntro.jsx';

describe('ManagerIntro', () => {
  it('링크를 누르면 onLink', () => {
    const onLink = vi.fn();
    const { container } = render(<ManagerIntro title="교육받은 매니저님이 방문합니다." linkLabel="어떤 교육을 받나요?" onLink={onLink} />);
    fireEvent.click(screen.getByRole('button', { name: '어떤 교육을 받나요?' }));
    expect(onLink).toHaveBeenCalledTimes(1);
    expect(container.querySelector('img')).toHaveAttribute('alt', '');
  });
});
