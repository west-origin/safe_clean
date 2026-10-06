// ScreenLayout 테스트: 슬롯들이 Figma 순서(StatusBar → navBar → Content → 하단 영역)로 놓이는지,
// navBar 가 있으면 Content 에 .withNav(상단 여백 8)가 붙는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ScreenLayout from './ScreenLayout.jsx';

describe('ScreenLayout', () => {
  // container.firstChild 는 .screen 틀. 그 자식들의 글자를 순서대로 모은다 — 첫 칸 '' 은 글자 없는 StatusBar 자리
  it('navBar · content · stickyCta · tabBar 순서로 놓인다', () => {
    const { container } = render(
      <ScreenLayout navBar={<header>nav</header>} tabBar={<nav>tab</nav>} stickyCta={<button type="button">cta</button>}>
        <p>본문</p>
      </ScreenLayout>
    );
    const texts = [...container.firstChild.children].map((el) => el.textContent);
    expect(texts).toEqual(['', 'nav', '본문', 'ctatab']); // 마지막은 하단 고정 영역(StickyCTA + TabBar)
    expect(screen.getByRole('main')).toHaveTextContent('본문');
    expect(screen.getByRole('main').className).toContain('withNav');
  });
});
