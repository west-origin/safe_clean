// StepQuestion 테스트: 질문이 제목(heading)으로 드러나고 서비스 이름·안내 문구가 화면에 있는지 확인한다.
import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StepQuestion from './StepQuestion.jsx';

test('서비스·제목·안내를 그린다', () => {
  render(<StepQuestion service="가전수리" title="어떤 가전을 수리할까요?" description="수리가 필요한 가전 한 가지를 선택해주세요." />);
  expect(screen.getByRole('heading', { name: '어떤 가전을 수리할까요?' })).toBeInTheDocument();
  expect(screen.getByText('가전수리')).toBeInTheDocument();
  expect(screen.getByText('수리가 필요한 가전 한 가지를 선택해주세요.')).toBeInTheDocument();
});
