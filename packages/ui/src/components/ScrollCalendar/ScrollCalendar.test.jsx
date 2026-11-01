// ScrollCalendar 테스트: 지난 날이 꺼지고 오늘은 오늘로, 고르면 onChange
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ScrollCalendar from './ScrollCalendar.jsx';

describe('ScrollCalendar', () => {
  it('끈 날·오늘·선택', () => {
    const onChange = vi.fn();
    render(<ScrollCalendar months={[{ year: 2026, month: 11 }]} todayKey="2026-11-02" value="2026-11-05" onChange={onChange} isDisabled={(d) => d.getDate() < 2} />);
    expect(screen.getByRole('button', { name: '11월 1일' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '11월 2일 오늘' })).toHaveTextContent('오늘');
    expect(screen.getByRole('button', { name: '11월 5일' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '11월 6일' }));
    expect(onChange).toHaveBeenCalledWith('2026-11-06');
  });
});
