import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SlideCounter from './SlideCounter.jsx';

// SlideCounter 테스트: aria-label("2개 중 2번째")로 요소를 찾고, 보이는 글자가 두 자리 "02/02" 인지 본다.

describe('SlideCounter', () => {
  it('두 자리 카운터를 표시한다', () => {
    render(<SlideCounter current={2} total={2} />);
    expect(screen.getByLabelText('2개 중 2번째')).toHaveTextContent('02/02');
  });
});
