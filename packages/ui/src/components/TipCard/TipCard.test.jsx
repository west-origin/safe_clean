// TipCard 테스트: 제목·본문이 그려지고, 카드가 제목을 이름으로 가진 complementary 영역(<aside>)으로 찾아지는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import TipCard from './TipCard.jsx';

describe('TipCard', () => {
  it('제목과 본문을 그린다', () => {
    render(
      <TipCard icon={{ src: 'a.webp', srcSet: 'a.webp 1x', width: 50, height: 49 }} title="이렇게 요청해보세요!">
        {'-특히 신경 써줬으면 하는 공간이나 가구가 있다면 알려주세요.'}
      </TipCard>,
    );
    expect(screen.getByRole('complementary', { name: '이렇게 요청해보세요!' })).toHaveTextContent('특히 신경');
  });
});
