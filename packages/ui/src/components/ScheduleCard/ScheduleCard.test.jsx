// ScheduleCard 테스트: 시간·상태 배지 문구·서비스명이 보이는지, onClick 을 주면 카드가 버튼이 되어 누를 수 있는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ScheduleCard from './ScheduleCard.jsx';

describe('ScheduleCard', () => {
  it('시간·배지·고객·서비스를 보여주고 onClick 이 있으면 버튼이 된다', () => {
    const onClick = vi.fn();
    render(
      <ScheduleCard time="10:00 - 13:00" status="scheduled" customer="김○○ 고객 · 강남구 역삼동" service="아파트 기본청소" onClick={onClick} />
    );
    const card = screen.getByRole('button');
    expect(card).toHaveTextContent('10:00 - 13:00');
    expect(card).toHaveTextContent('방문 예정');
    expect(card).toHaveTextContent('아파트 기본청소');
    fireEvent.click(card);
    expect(onClick).toHaveBeenCalled();
  });
});
