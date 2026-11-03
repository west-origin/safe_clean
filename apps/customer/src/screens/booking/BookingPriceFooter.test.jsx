// 예약 하단 줄(BookingPriceFooter) 테스트: '총 결제금액' 글자 + 금액, '자세히' 없음, "다음" 이 disabled·onNext 를 따른다, children 은 줄 위에.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import BookingPriceFooter from './BookingPriceFooter.jsx';

describe('BookingPriceFooter', () => {
  it('총 결제금액 + 금액 + 다음', () => {
    const onNext = vi.fn();
    const { rerender } = render(<BookingPriceFooter price={62400} onNext={onNext}><p>안내</p></BookingPriceFooter>);
    expect(screen.getByText('총 결제금액')).toBeInTheDocument();
    expect(screen.getByText('62,400원')).toBeInTheDocument();
    expect(screen.getByText('안내')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '자세히' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(onNext).toHaveBeenCalledTimes(1);
    rerender(<BookingPriceFooter price={0} onNext={onNext} disabled />);
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
  });
});
