import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ManagerTabBar from './ManagerTabBar.jsx';
import { MANAGER_TABS } from './tabs.js';

// ManagerTabBar 테스트: 탭 순서·문구, 활성 탭의 aria-current, 탭을 누를 때 onSelect(key), 콜 배지 표시를 확인한다.
describe('ManagerTabBar', () => {
  it('탭 5개가 Figma 순서·문구대로 있고 활성 탭에 aria-current 가 붙는다', () => {
    render(<ManagerTabBar active="schedule" />);
    const tabs = screen.getAllByRole('button');
    expect(tabs.map((t) => t.textContent)).toEqual(['홈', '콜', '일정', '정산', 'MY']);
    expect(MANAGER_TABS.map((t) => t.key)).toEqual(['home', 'call', 'schedule', 'settlement', 'my']);
    expect(screen.getByRole('button', { name: '일정' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '홈' })).not.toHaveAttribute('aria-current');
  });

  it('탭을 누르면 onSelect(key) 가 호출된다', () => {
    const onSelect = vi.fn();
    render(<ManagerTabBar active="home" onSelect={onSelect} />);
    fireEvent.click(screen.getByRole('button', { name: '정산' }));
    expect(onSelect).toHaveBeenCalledWith('settlement');
  });

  it('showCallBadge 면 콜 탭에 배지 숫자가 보인다', () => {
    // 배지를 켠 채 그려 aria-label 로 찾고, 배지를 끈 props 로 다시 그려(rerender) 사라졌는지 본다
    const { rerender } = render(<ManagerTabBar active="home" showCallBadge callBadgeCount="2" />);
    expect(screen.getByLabelText('새 콜 2건')).toHaveTextContent('2');
    rerender(<ManagerTabBar active="home" />);
    expect(screen.queryByLabelText(/새 콜/)).toBeNull();
  });
});
