import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusBadge from './StatusBadge.jsx';
import { STATUS } from './status.js';

// StatusBadge 테스트: status 전부가 Figma 문구와 아이콘(svg)으로 그려지는지, label 오버라이드, 모르는 status 의 오류.

describe('StatusBadge', () => {
  it('Figma Status variant 7종이 전부 Figma 문구로 렌더된다', () => {
    const expected = {
      new: '신규', scheduled: '방문 예정', inProgress: '진행 중', live: 'LIVE',
      paused: '일시정지', complete: '완료', settlementPending: '정산 예정',
    };
    // status.js 의 키 목록·순서가 기대 표와 똑같아야 한다 (하나라도 빠지거나 늘면 실패)
    expect(Object.keys(STATUS)).toEqual(Object.keys(expected));
    for (const [status, label] of Object.entries(expected)) {
      // 매번 그렸다가 unmount 로 치워야 다음 반복의 getByText 가 이전 배지와 섞이지 않는다
      const { container, unmount } = render(<StatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      expect(container.querySelector('svg')).not.toBeNull();
      unmount();
    }
  });

  it('label 로 문구를 오버라이드할 수 있다 (Figma 인스턴스 오버라이드)', () => {
    render(<StatusBadge status="complete" label="정상 연결" />);
    expect(screen.getByText('정상 연결')).toBeInTheDocument();
  });

  it('알 수 없는 status 는 에러를 던진다', () => {
    expect(() => render(<StatusBadge status="unknown" />)).toThrow();
  });
});
