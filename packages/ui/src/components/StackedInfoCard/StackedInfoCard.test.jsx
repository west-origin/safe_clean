import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StackedInfoCard from './StackedInfoCard.jsx';

// StackedInfoCard 테스트: value 행은 값 문구가, children 행은 넘긴 자유 내용이 화면에 나오는지 본다.

test('값 행과 자유 내용 행', () => {
  render(<StackedInfoCard rows={[{ icon: 'calendar', label: '방문 일정', value: '9월 23일(수)' }, { icon: 'person', label: '기사 배정', children: <span>배정 중</span> }]} />);
  expect(screen.getByText('9월 23일(수)')).toBeInTheDocument();
  expect(screen.getByText('배정 중')).toBeInTheDocument();
});
