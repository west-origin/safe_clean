// TimeSlotGrid 테스트: 오전 4칸 + 오후 6칸이 radio 로 그려지고, value 와 같은 칸이 aria-checked="true" 이며,
// 다른 칸을 누르면 onChange 가 그 시간 문구로 불리는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TimeSlotGrid from './TimeSlotGrid.jsx';

// 테스트용 시간 목록 (Figma 의 09:00~12:00 / 13:00~18:00 과 같은 모양)
const slots = { am: ['09:00', '10:00', '11:00', '12:00'], pm: ['13:00', '14:00', '15:00', '16:00', '17:00', '18:00'] };

describe('TimeSlotGrid', () => {
  it('오전/오후 슬롯을 그리고 선택 상태가 radio 로 드러난다', () => {
    const onChange = vi.fn();
    render(<TimeSlotGrid slots={slots} value="11:00" onChange={onChange} />);
    expect(screen.getAllByRole('radio')).toHaveLength(10);
    expect(screen.getByRole('radio', { name: '11:00' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('radio', { name: '14:00' }));
    expect(onChange).toHaveBeenCalledWith('14:00');
  });
});
