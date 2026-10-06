import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SelectionSummary from './SelectionSummary.jsx';

// SelectionSummary 테스트: 기본 제목(heading), badge 칩 문구, 행의 값이 화면에 나오는지 본다.

test('제목·단계 칩·행을 그린다', () => {
  render(<SelectionSummary badge="3/4 단계" rows={[{ label: '서비스', value: '가전수리' }, { label: '가전', value: '냉장고' }]} />);
  expect(screen.getByRole('heading', { name: '현재 선택' })).toBeInTheDocument();
  expect(screen.getByText('3/4 단계')).toBeInTheDocument();
  expect(screen.getByText('냉장고')).toBeInTheDocument();
});
