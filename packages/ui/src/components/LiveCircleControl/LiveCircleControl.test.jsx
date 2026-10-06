import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import LiveCircleControl from './LiveCircleControl.jsx';

// 포커스 테두리 색을 확인하려고 CSS 파일 내용을 글자로 읽어 둔다
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'LiveCircleControl.module.css'), 'utf8');

// LiveCircleControl 테스트: Figma Type 3종(pause·back15·forward15)이 각각 라벨 이름의 버튼으로 그려지고
// 클릭이 onClick 으로 전달되는지 본다. 한 type 을 확인할 때마다 unmount 로 치우고 다음 type 을 그린다.
describe('LiveCircleControl', () => {
  it('Type 3종이 아이콘 버튼으로 렌더되고 aria-label 이 있다', () => {
    const onClick = vi.fn();
    for (const [type, label] of [['pause', '일시정지'], ['back15', '15초 뒤로'], ['forward15', '15초 앞으로']]) {
      const { unmount } = render(<LiveCircleControl type={type} onClick={onClick} />);
      fireEvent.click(screen.getByRole('button', { name: label }));
      unmount();
    }
    expect(onClick).toHaveBeenCalledTimes(3);
  });

  // tone: 기본(onVideo)은 영상 위라 흰 포커스 테두리, onLight 는 밝은 화면 배경(세로 녹화본) 위라 브랜드 파랑 테두리
  it('tone 2종이 클래스로 드러나고, onLight 의 포커스 테두리는 흰색이 아니다', () => {
    const { unmount } = render(<LiveCircleControl type="pause" />);
    expect(screen.getByRole('button', { name: '일시정지' }).className).toContain('onVideo');
    unmount();
    render(<LiveCircleControl type="pause" tone="onLight" />);
    expect(screen.getByRole('button', { name: '일시정지' }).className).toContain('onLight');
    const rule = css.match(/\.onLight:focus-visible\s*\{([^}]*)\}/);
    expect(rule?.[1]).toContain('var(--color-brand-primary)');
  });
});
