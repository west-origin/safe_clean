// BookingInfoCard 테스트: 행 4개를 넘기면 라벨(dt)이 4개 그려지고, 값 글자(예: 11:00)가 화면에 있는지 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import BookingInfoCard from './BookingInfoCard.jsx';

describe('BookingInfoCard', () => {
  it('행 4개를 라벨/값으로 그린다', () => {
    render(
      <BookingInfoCard
        rows={[
          { icon: 'calendar', label: '예약일', value: '2026년 9월 16일 (수)' },
          { icon: 'clock', label: '예약시간', value: '11:00' },
          { icon: 'pin', label: '서비스 주소', value: '테헤란로 123' },
          { icon: 'person', label: '담당 클리너', value: '배정 후 알려드려요' },
        ]}
      />,
    );
    expect(screen.getAllByRole('term')).toHaveLength(4); // dt 요소의 접근성 역할이 term 이다 → 라벨 4개
    expect(screen.getByText('11:00')).toBeInTheDocument();
  });
});
