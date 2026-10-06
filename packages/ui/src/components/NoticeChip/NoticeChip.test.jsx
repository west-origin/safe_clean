// NoticeChip 검증: 선택 여부가 aria-pressed 로 드러나는지, 누르면 onToggle 에 반대값이 오는지,
// 아이콘 표(NOTICE_ICONS)의 이름이 전부 그려지는지(없는 이름은 오류).
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import NoticeChip from './NoticeChip.jsx';
import { NOTICE_ICONS } from '../../icons/noticeIcons.js';

describe('NoticeChip', () => {
  it('Default/Selected 가 aria-pressed 로 드러나고 누르면 onToggle(반대값)', () => {
    const onToggle = vi.fn();
    render(<NoticeChip label="반려동물" icon="pet" selected={false} onToggle={onToggle} />);
    const chip = screen.getByRole('button', { name: '반려동물' });
    expect(chip).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(chip);
    expect(onToggle).toHaveBeenCalledWith(true);
  });

  // 아이콘 표의 이름마다 칩을 하나씩 그려 svg 가 나오는지 보고, 그릴 때마다 unmount 로 치운다(다음 칩과 섞이지 않게).
  // 마지막 줄은 없는 이름("nope")이면 render 가 오류를 던지는지 확인한다.
  it('NoticeIcon 18개가 전부 icon 이름으로 쓰인다', () => {
    expect(Object.keys(NOTICE_ICONS).length).toBe(18);
    for (const name of Object.keys(NOTICE_ICONS)) {
      const { container, unmount } = render(<NoticeChip label={name} icon={name} selected={false} />);
      expect(container.querySelector('svg')).not.toBeNull();
      unmount();
    }
    expect(() => render(<NoticeChip label="x" icon="nope" />)).toThrow();
  });
});
