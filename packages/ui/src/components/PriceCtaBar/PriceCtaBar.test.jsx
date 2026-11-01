// PriceCtaBar 테스트: 금액·자세히·오른쪽 버튼
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import PriceCtaBar from './PriceCtaBar.jsx';

describe('PriceCtaBar', () => {
  it('자세히를 누르면 onDetail', () => {
    const onDetail = vi.fn();
    render(<PriceCtaBar priceLabel="41,600원" onDetail={onDetail}><button type="button">다음</button></PriceCtaBar>);
    expect(screen.getByText('41,600원')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '자세히' }));
    expect(onDetail).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument();
  });
});
