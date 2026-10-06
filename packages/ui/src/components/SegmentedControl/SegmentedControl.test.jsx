// SegmentedControl 테스트: 탭이 role="tab" 으로 드러나고 활성 탭에 aria-selected 가 붙는지, 누르면 그 탭의 key 로 onChange 를 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SegmentedControl from './SegmentedControl.jsx';

const OPTIONS = [{ key: 'today', label: '오늘' }, { key: 'week', label: '이번 주' }, { key: 'calendar', label: '달력' }];

describe('SegmentedControl', () => {
  it('탭이 role=tab 으로 렌더되고 활성 탭에 aria-selected, 누르면 onChange(key)', () => {
    const onChange = vi.fn();
    render(<SegmentedControl options={OPTIONS} value="today" onChange={onChange} ariaLabel="기간" />);
    expect(screen.getByRole('tab', { name: '오늘' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(screen.getByRole('tab', { name: '달력' }));
    expect(onChange).toHaveBeenCalledWith('calendar');
  });
});
