// ReservationCard 테스트: 상태(status)별 배지 문구와 아래 버튼(변경·취소 / 실시간 영상 보기 / 없음),
// changeable=false 일 때 안내 한 줄, 버튼이 각 콜백을 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ReservationCard from './ReservationCard.jsx';

// CSS Module 파일을 글자 그대로 읽어 둔다 — 탭 요소에 터치 영역 변수(--touch-target)를 썼는지 글자로 확인한다
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'ReservationCard.module.css'), 'utf8');
const rows = [
  { label: '일정', value: '2026년 11월 1일 (일) 오전 9시' },
  { label: '주소', value: '부산광역시 중앙대로 2127-23' },
  { label: '매니저', value: '김미영 매니저' },
];

describe('ReservationCard', () => {
  it('제목·상태·정보·버튼이 렌더되고 두 버튼이 각각 동작한다', () => {
    const onChange = vi.fn();
    const onCancel = vi.fn();
    render(<ReservationCard title="가정/원룸 청소" status="scheduled" rows={rows} onChange={onChange} onCancel={onCancel} />);
    expect(screen.getByRole('article', { name: '가정/원룸 청소' })).toHaveTextContent('방문 예정');
    expect(screen.getByText('김미영 매니저')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '예약 변경' }));
    fireEvent.click(screen.getByRole('button', { name: '예약 취소' }));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });

  // 진행 중 카드의 버튼을 눌러 onWatch 확인 → 같은 카드를 완료(녹화본 없음)로 다시 그려 버튼이 없어지는지 본다
  it('진행 중이면 "실시간 영상 보기", 완료/취소는 버튼 없음', () => {
    const onWatch = vi.fn();
    const { rerender } = render(<ReservationCard title="가정/원룸 청소" status="inProgress" rows={rows} onWatch={onWatch} />);
    fireEvent.click(screen.getByRole('button', { name: '실시간 영상 보기' }));
    expect(onWatch).toHaveBeenCalledTimes(1);
    rerender(<ReservationCard title="가정/원룸 청소" status="done" rows={rows} />);
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByText('완료')).toBeInTheDocument();
  });

  it('status=assigning 은 "매니저 배정 중"', () => {
    render(<ReservationCard title="가전청소" status="assigning" rows={rows} />);
    expect(screen.getByText('매니저 배정 중')).toBeInTheDocument();
  });

  it('changeable=false 면 변경·취소 버튼 대신 안내 한 줄', () => {
    render(<ReservationCard title="가정/원룸 청소" status="scheduled" rows={[]} changeable={false} />);
    expect(screen.queryByRole('button', { name: '예약 취소' })).toBeNull();
    expect(screen.getByText('방문 24시간 전까지 변경·취소할 수 있어요')).toBeInTheDocument();
  });
});
