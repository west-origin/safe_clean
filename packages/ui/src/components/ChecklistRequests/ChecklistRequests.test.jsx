import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ChecklistRequests from './ChecklistRequests.jsx';

const copy = { requestsTitle: '고객 요청사항', requestsStatus: '완료 여부 미확인', requestsEmpty: '등록된 요청사항 정보가 제공되지 않았습니다.', requestsNote: '청소 진행률과 별도로 완료 상태를 표시합니다.' };
describe('ChecklistRequests', () => {
  it('요청이 없으면 빈 문구, 있으면 목록을 보여 준다', () => {
    const { rerender } = render(<ChecklistRequests copy={copy} />);
    expect(screen.getByText(copy.requestsEmpty)).toBeInTheDocument();
    rerender(<ChecklistRequests copy={copy} requests={['문 닫기', '문 닫기']} />);
    expect(screen.queryByText(copy.requestsEmpty)).toBeNull();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
    expect(screen.getByText(copy.requestsStatus)).toBeInTheDocument();
  });
});
