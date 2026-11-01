// FaqCard 테스트: 질문 글자가 DOM 에 있고, 장식용 Q 는 보조기기에서 숨는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import FaqCard from './FaqCard.jsx';

describe('FaqCard', () => {
  it('질문을 그리고 Q 배지는 aria-hidden', () => {
    render(<FaqCard question="요청사항을 전달할 수 있나요?" />);
    expect(screen.getByText('요청사항을 전달할 수 있나요?')).toBeInTheDocument();
    expect(screen.getByText('Q')).toHaveAttribute('aria-hidden', 'true');
  });
});
