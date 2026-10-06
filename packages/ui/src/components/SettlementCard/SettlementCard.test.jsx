import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import SettlementCard from './SettlementCard.jsx';

// SettlementCard 테스트: onClick 을 주면 카드가 버튼이 되고, 배지 문구(status=complete → '완료')와 금액이 보이며, 누르면 onClick 이 불린다.

describe('SettlementCard', () => {
  it('날짜·배지·업무·금액을 렌더하고 onClick 이면 버튼', () => {
    const onClick = vi.fn();
    render(<SettlementCard date="9월 14일" status="complete" title="강남구 기본청소" amount="120,000원" onClick={onClick} />);
    const card = screen.getByRole('button');
    expect(card).toHaveTextContent('완료');
    expect(card).toHaveTextContent('120,000원');
    fireEvent.click(card);
    expect(onClick).toHaveBeenCalled();
  });
});
