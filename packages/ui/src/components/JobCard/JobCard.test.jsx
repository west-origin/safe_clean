import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import JobCard from './JobCard.jsx';

// JobCard 테스트: 제목(heading)·상태 배지('scheduled' 는 '방문 예정' 문구)·정보 행 값이 그려지고,
// 버튼을 누르면 onCta 가 불리는지 확인한다.
describe('JobCard', () => {
  it('시간·배지·제목·정보 행·CTA 버튼을 렌더하고 CTA 가 onCta 를 부른다', () => {
    const onCta = vi.fn();
    render(
      <JobCard
        time="10:00"
        status="scheduled"
        title="강남구 역삼동 · 아파트 기본청소"
        rows={[{ label: '예상 소요시간', value: '3시간' }, { label: '예상 정산', value: '85,000원' }]}
        ctaLabel="업무 상세보기"
        onCta={onCta}
      />
    );
    expect(screen.getByRole('heading', { name: '강남구 역삼동 · 아파트 기본청소' })).toBeInTheDocument();
    expect(screen.getByText('방문 예정')).toBeInTheDocument();
    expect(screen.getByText('85,000원')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '업무 상세보기' }));
    expect(onCta).toHaveBeenCalled();
  });
});
