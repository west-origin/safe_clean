// RealtimePill 테스트: 기본 글자("실시간")와 label 로 바꾼 글자가 보이는지, 장식 점이 aria-hidden 인지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import RealtimePill from './RealtimePill.jsx';

describe('RealtimePill', () => {
  // container 는 그린 결과를 감싼 DOM 요소 — 역할로 찾을 수 없는 장식 점은 querySelector 로 직접 찾는다
  it('기본 라벨 "실시간" 을 그리고 점은 보조기기에 숨긴다', () => {
    const { container } = render(<RealtimePill />);
    expect(screen.getByText('실시간')).toBeInTheDocument();
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeNull();
    render(<RealtimePill label="LIVE" />);
    expect(screen.getByText('LIVE')).toBeInTheDocument();
  });
});
