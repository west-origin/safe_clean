// SummaryRow 테스트: 라벨·값이 보이고, tone(success)·emphasis(heading) 가 값의 클래스 이름으로 드러나는지 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SummaryRow from './SummaryRow.jsx';

describe('SummaryRow', () => {
  it('라벨·값을 렌더하고 tone/emphasis 가 클래스로 드러난다', () => {
    render(<SummaryRow label="체크리스트" value="9개 중 9개 완료" tone="success" emphasis="heading" />);
    const value = screen.getByText('9개 중 9개 완료');
    expect(value.className).toContain('success');
    expect(value.className).toContain('text-heading-s');
    expect(screen.getByText('체크리스트')).toBeInTheDocument();
  });
});
