// BeforeAfterSlider 테스트: 두 사진이 위치 이름과 함께 그려지는지, 키보드용 slider 가 값을 바꾸면 막대 위치(--position)가 따라오는지,
// 손가락으로 누른 자리로 막대가 옮겨 가는지, 손잡이가 터치 타겟 크기를 쓰는지 확인한다.
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { render, screen, fireEvent } from '@testing-library/react';
import BeforeAfterSlider from './BeforeAfterSlider.jsx';

const props = { beforeSrc: '/before.jpg', afterSrc: '/after.jpg', location: '거실 소파 앞' };

describe('BeforeAfterSlider', () => {
  it('청소 전·후 사진을 위치 이름과 함께 그리고 처음 막대는 가운데다', () => {
    render(<BeforeAfterSlider {...props} />);
    expect(screen.getByAltText('거실 소파 앞 청소 전')).toHaveAttribute('src', '/before.jpg');
    expect(screen.getByAltText('거실 소파 앞 청소 후')).toHaveAttribute('src', '/after.jpg');
    const slider = screen.getByRole('slider', { name: '거실 소파 앞 청소 전후 비교' });
    expect(slider).toHaveValue('50');
    expect(slider).toHaveAttribute('aria-valuetext', '청소 전 50% · 청소 후 50%');
  });

  it('slider 값을 바꾸면 막대 위치가 따라온다', () => {
    const { container } = render(<BeforeAfterSlider {...props} />);
    fireEvent.change(screen.getByRole('slider'), { target: { value: '20' } });
    expect(container.firstChild.style.getPropertyValue('--position')).toBe('20%');
    expect(screen.getByRole('slider')).toHaveAttribute('aria-valuetext', '청소 전 20% · 청소 후 80%');
  });

  it('누른 자리로 막대를 옮기고 끄는 동안 따라간다', () => {
    const { container } = render(<BeforeAfterSlider {...props} />);
    const frame = container.firstChild;
    frame.getBoundingClientRect = () => ({ left: 0, width: 200, top: 0, height: 266, right: 200, bottom: 266 });
    // jsdom 의 PointerEvent 는 clientX 를 싣지 않아 같은 이름의 MouseEvent 로 보낸다
    const pointer = (type, clientX) => fireEvent(frame, new MouseEvent(type, { bubbles: true, clientX }));
    pointer('pointerdown', 50);
    expect(frame.style.getPropertyValue('--position')).toBe('25%');
    pointer('pointermove', 250); // 틀 밖 → 100% 로 자른다
    expect(frame.style.getPropertyValue('--position')).toBe('100%');
    pointer('pointerup', 250);
    pointer('pointermove', 0); // 손을 뗀 뒤 움직임은 무시
    expect(frame.style.getPropertyValue('--position')).toBe('100%');
  });

  it('손잡이는 터치 타겟 크기다', () => {
    const css = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'BeforeAfterSlider.module.css'), 'utf8');
    expect(css).toMatch(/width: var\(--touch-target\)/);
  });
});
