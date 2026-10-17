import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ChecklistProgressCard from './ChecklistProgressCard.jsx';

const copy = { title: '청소 체크리스트', basis: '매니저 완료 체크 기준', requestsTitle: '고객 요청사항', requestsStatus: '완료 여부 미확인', requestsEmpty: '등록된 요청사항 정보가 제공되지 않았습니다.', requestsNote: '청소 진행률과 별도로 완료 상태를 표시합니다.' };
describe('ChecklistProgressCard', () => {
  it('실제 항목 상태와 진행률을 읽을 수 있고 요청사항의 미확인 상태를 구분한다', () => {
    render(<ChecklistProgressCard copy={copy} checklist={{ done: 1, total: 2, percent: 50, groups: [{ group: '거실', done: 1, total: 2, items: [{ id: '1', label: '바닥', done: true }, { id: '2', label: '먼지', done: false }] }], requests: ['문을 닫아주세요'] }} />);
    expect(screen.getByRole('region', { name: copy.title })).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '1');
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuemax', '2');
    expect(screen.getByLabelText('바닥 완료')).toBeInTheDocument();
    expect(screen.getByLabelText('먼지 미완료')).toBeInTheDocument();
    expect(screen.getByText('매니저 완료 체크 기준 · 50%')).toBeInTheDocument();
    expect(screen.getByText('완료 여부 미확인')).toBeInTheDocument();
  });
});
