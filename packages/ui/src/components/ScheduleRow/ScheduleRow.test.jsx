// ScheduleRow 테스트: 값이 있으면 값+설명, 없으면 설명만 회색으로, 버튼은 onAction
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ScheduleRow from './ScheduleRow.jsx';

describe('ScheduleRow', () => {
  it('값·자리 글자와 버튼', () => {
    const onAction = vi.fn();
    const { rerender } = render(<ScheduleRow value="2시간" caption="서비스 시간" actionLabel="변경" onAction={onAction} />);
    expect(screen.getByText('2시간')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '서비스 시간 변경' }));
    expect(onAction).toHaveBeenCalledTimes(1);
    rerender(<ScheduleRow caption="방문 일" actionLabel="선택" />);
    expect(screen.getByText('방문 일')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '방문 일 선택' })).toHaveClass(/action/);
  });
});
