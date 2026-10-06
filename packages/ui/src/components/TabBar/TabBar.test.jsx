// 고객용 TabBar 테스트: 탭 4개의 순서·문구가 Figma 와 같은지, 활성 탭에 aria-current="page" 가 붙는지,
// 수리 탭이 1x/2x/3x 래스터 그림을 쓰는지, 탭을 누르면 onSelect 가 그 탭의 key 로 불리는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TabBar from './TabBar.jsx';

describe('TabBar (고객용)', () => {
  it('탭 4개가 Figma 순서·문구대로 있고 활성 탭에 aria-current 가 붙는다', () => {
    render(<TabBar active="reservations" />);
    expect(screen.getAllByRole('button').map((b) => b.textContent)).toEqual(['홈', '수리', '내 예약', '설정']);
    expect(screen.getByRole('button', { name: '내 예약' })).toHaveAttribute('aria-current', 'page');
  });

  it('수리 탭은 래스터 이미지(srcset 1x/2x/3x)를 쓰고, 탭을 누르면 onSelect(key)', () => {
    const onSelect = vi.fn();
    render(<TabBar active="home" onSelect={onSelect} />);
    const img = screen.getByRole('button', { name: '수리' }).querySelector('img');
    expect(img.getAttribute('srcset')).toMatch(/1x.*2x.*3x/);
    fireEvent.click(screen.getByRole('button', { name: '설정' }));
    expect(onSelect).toHaveBeenCalledWith('settings');
  });
});
