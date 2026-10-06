// CarouselDots 검증: 점 개수, 현재 위치를 알리는 이름, 누를 수 있는 tab 역할이 아닌지.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import CarouselDots from './CarouselDots.jsx';

describe('CarouselDots', () => {
  it('점 수만큼 그리고 현재 위치를 "n / count" 로 알린다 (점은 누를 수 없으므로 tab 이 아니다)', () => {
    const { container } = render(<CarouselDots index={1} count={2} />);
    expect(screen.getByRole('img', { name: '슬라이드 2 / 2' })).toBeInTheDocument();
    expect(container.querySelectorAll('span')).toHaveLength(2);
    expect(screen.queryByRole('tab')).toBeNull();
  });
});
