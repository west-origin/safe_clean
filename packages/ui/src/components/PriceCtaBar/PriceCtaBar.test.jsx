// PriceCtaBar 테스트: 금액·자세히·오른쪽 버튼, caption(금액 위 글자)
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

  it('caption 은 금액 위 글자, onDetail 이 없으면 자세히 버튼도 없다', () => {
    render(<PriceCtaBar priceLabel="62,400원" caption="총 결제금액"><button type="button">다음</button></PriceCtaBar>);
    expect(screen.getByText('총 결제금액')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '자세히' })).toBeNull();
  });
});
