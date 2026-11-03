// ScheduleAccordion 테스트: 머리줄 버튼이 aria-expanded 로 열림을 알리고, 열렸을 때만 내용이 있다. 누르면 onToggle.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ScheduleAccordion from './ScheduleAccordion.jsx';

describe('ScheduleAccordion', () => {
  it('닫힘 → 열림', () => {
    const onToggle = vi.fn();
    const { rerender } = render(<ScheduleAccordion icon="calendar" label="방문 일" open={false} onToggle={onToggle}><p>달력</p></ScheduleAccordion>);
    const header = screen.getByRole('button', { name: '방문 일' });
    expect(header).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('달력')).toBeNull();
    fireEvent.click(header);
    expect(onToggle).toHaveBeenCalled();
    rerender(<ScheduleAccordion icon="calendar" label="방문 일" open onToggle={onToggle}><p>달력</p></ScheduleAccordion>);
    expect(header).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('달력')).toBeInTheDocument();
  });
});
