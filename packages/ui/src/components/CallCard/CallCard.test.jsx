// CallCard 검증: Figma 기본 문구·props 렌더와 거절·수락 콜백, onOpen 이 있을 때만 생기는 카드 탭 버튼,
// details 줄 표시와 eta 가 없을 때 이동 시간 줄 생략.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import CallCard from './CallCard.jsx';

describe('CallCard', () => {
  it('Figma 기본 문구(새로운 청소 요청/예상 정산/거절/수락)와 props 를 렌더하고 버튼이 콜백을 부른다', () => {
    const onAccept = vi.fn();
    const onReject = vi.fn();
    render(
      <CallCard
        expiry="4:32 후 만료"
        title="9월 18일 목요일 오후 2:00"
        location="서울 강남구 역삼동 · 아파트 기본청소"
        pay="85,000원"
        eta="현재 위치 기준 이동 약 25분"
        note="특이사항: 반려묘 1마리"
        onAccept={onAccept}
        onReject={onReject}
      />
    );
    expect(screen.getByText('새로운 청소 요청')).toBeInTheDocument();
    expect(screen.getByText('4:32 후 만료')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '9월 18일 목요일 오후 2:00' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '거절' }));
    fireEvent.click(screen.getByRole('button', { name: '수락' }));
    expect(onReject).toHaveBeenCalledTimes(1);
    expect(onAccept).toHaveBeenCalledTimes(1);
  });

  it('onOpen 이 있으면 카드 탭용 버튼이 생기고, 없으면 없다', () => {
    const onOpen = vi.fn();
    const { rerender } = render(<CallCard expiry="e" title="9월 18일" location="l" pay="p" eta="t" onOpen={onOpen} />);
    fireEvent.click(screen.getByRole('button', { name: '9월 18일 요청 상세 보기' }));
    expect(onOpen).toHaveBeenCalledTimes(1);
    // 같은 카드를 onOpen 없이 다시 그리면(상세 화면처럼) 탭 버튼이 없어야 한다
    rerender(<CallCard expiry="e" title="9월 18일" location="l" pay="p" eta="t" />);
    expect(screen.queryByRole('button', { name: /상세 보기/ })).toBeNull();
  });

  it('details 는 수락 전 정보로 줄줄이 보이고, eta 가 없으면 이동 시간 줄을 그리지 않는다 (사용성 검토 2026-09-22)', () => {
    render(<CallCard expiry="4:32 후 만료" title="9월 18일 목요일 오후 2:00" location="서울 강남구 · 가정/원룸 청소" pay="85,000원" details={['예상 소요 3시간', '아파트·빌라 · 25평']} />);
    expect(screen.getByText('예상 소요 3시간')).toBeInTheDocument();
    expect(screen.getByText('아파트·빌라 · 25평')).toBeInTheDocument();
    expect(screen.queryByText(/이동/)).toBeNull(); // eta 를 넘기지 않았으니 '이동' 이 든 글자가 없어야 한다
  });
});
