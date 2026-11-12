// PriceGuide 테스트: 넘긴 부분만 그리고, 금액은 쉼표·'상담'으로 보인다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PriceGuide from './PriceGuide.jsx';

describe('PriceGuide', () => {
  it('요금표·카드·범위를 그리고 금액이 없으면 상담을 보인다', () => {
    render(<PriceGuide guide={{
      icon: 'house', eyebrow: '요금 안내', title: '가정 정기청소', subtitle: '설명',
      plans: [{ title: '주 1회', sub: '한 달 4회 방문', price: 329000 }, { title: '주 4회 이상', sub: '원하는 횟수만큼' }],
      cards: [{ tone: 'default', pill: '처음이라면', title: '첫 1회 체험', amount: 50000, note: '1회 3시간' }],
      scopeTitle: '범위', scope: ['현관'], footnote: '각주',
    }} />);
    expect(screen.getByRole('heading', { name: '가정 정기청소' })).toBeInTheDocument();
    expect(screen.getByText('329,000')).toBeInTheDocument();
    expect(screen.getByText('상담')).toBeInTheDocument();
    expect(screen.getByText('50,000')).toBeInTheDocument();
    expect(screen.getByText('* 각주')).toBeInTheDocument();
  });
  it('크기별 요금·추가 요금·혜택 띠를 그린다', () => {
    render(<PriceGuide guide={{
      icon: 'disassembly', eyebrow: '견적 안내', title: '후드', subtitle: '',
      sizesTitle: '후드 크기별 요금', sizes: [{ size: 's', title: '600mm', sub: '일반 주방', price: 130000 }],
      extras: { title: '추가 요금', aside: '해당될 때만', rows: [{ label: '콘센트', price: 20000 }] },
      benefits: [{ icon: 'gift', caption: '첫 결제 시', title: '1회 무료' }],
    }} />);
    expect(screen.getByText('130,000')).toBeInTheDocument();
    expect(screen.getByText('+20,000')).toBeInTheDocument();
    expect(screen.getByText('1회 무료')).toBeInTheDocument();
  });
});
