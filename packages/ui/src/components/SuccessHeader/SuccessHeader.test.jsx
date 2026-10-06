// SuccessHeader 테스트: 제목이 heading 역할과 이름으로 드러나는지 확인한다.
import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SuccessHeader from './SuccessHeader.jsx';

test('제목·안내', () => {
  render(<SuccessHeader title="예약이 완료되었어요!" description="방문 일정과 주소를 확인해 주세요." />);
  expect(screen.getByRole('heading', { name: '예약이 완료되었어요!' })).toBeInTheDocument();
});
