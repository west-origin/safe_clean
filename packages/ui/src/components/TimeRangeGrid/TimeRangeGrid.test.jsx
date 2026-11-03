// TimeRangeGrid 테스트: 첫 탭 = 시작, 뒤 시간 탭 = 종료(최소·최대 시간 밖은 꺼짐), 사이 칸 표시, 앞 시간 탭 = 새 시작.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TimeRangeGrid from './TimeRangeGrid.jsx';
import { hoursBetween } from './timeRange.js';

const TIMES = ['09:00', '10:00', '11:00', '12:00', '13:00'];

describe('TimeRangeGrid', () => {
  it('시작 → 종료', () => {
    const onChange = vi.fn();
    const { rerender } = render(<TimeRangeGrid times={TIMES} onChange={onChange} isStartDisabled={(t) => t === '13:00'} />);
    expect(screen.getByRole('button', { name: '13:00' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '09:00' }));
    expect(onChange).toHaveBeenLastCalledWith({ start: '09:00', end: null });

    rerender(<TimeRangeGrid times={TIMES} start="09:00" onChange={onChange} minHours={2} maxHours={3} />);
    expect(screen.getByRole('button', { name: '09:00 시작' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '10:00' })).toBeDisabled(); // 1시간 < 최소 2
    expect(screen.getByRole('button', { name: '13:00' })).toBeDisabled(); // 4시간 > 최대 3
    fireEvent.click(screen.getByRole('button', { name: '12:00' }));
    expect(onChange).toHaveBeenLastCalledWith({ start: '09:00', end: '12:00' });

    rerender(<TimeRangeGrid times={TIMES} start="09:00" end="12:00" onChange={onChange} />);
    expect(screen.getByRole('button', { name: '12:00 종료' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '10:00' })).toHaveAttribute('aria-pressed', 'true'); // 사이
    expect(screen.getByRole('button', { name: '13:00' })).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(screen.getByRole('button', { name: '11:00' }));
    expect(onChange).toHaveBeenLastCalledWith({ start: '11:00', end: null }); // 다 고른 뒤 탭 = 새 시작
  });

  it('hoursBetween', () => {
    expect(hoursBetween('09:00', '12:30')).toBe(3.5);
  });
});
