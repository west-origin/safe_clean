import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import LiveProgress from './LiveProgress.jsx';
import IconButton from '../IconButton/IconButton.jsx';

// LiveProgress 와 IconButton 을 함께 검사한다.
// CSS 파일 내용을 글자로 읽어 두고, 터치 영역 변수(--touch-target)가 쓰였는지 아래에서 확인한다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'LiveProgress.module.css'), 'utf8');

describe('LiveProgress · IconButton', () => {
  it('구간이 있어도 같은 비율로 탐색하고 현재 구간을 접근성 문구에 표시한다', () => {
    const onSeek = vi.fn();
    const chapters = [{ start: 0, end: 0.25, title: '바닥' }, { start: 0.25, end: 1, title: '' }];
    const { container } = render(<LiveProgress ratio={0.2} timeLabel="2:00 / 10:00" chapters={chapters} chapterTitle="바닥" onSeek={onSeek} />);
    const slider = screen.getByRole('slider');
    expect(slider).toHaveAttribute('aria-valuetext', '2:00 / 10:00 · 바닥');
    slider.getBoundingClientRect = () => ({ left: 0, width: 200 });
    fireEvent.click(slider, { clientX: 50 });
    expect(onSeek).toHaveBeenCalledWith(0.25);
    expect(container.querySelector('[aria-hidden="true"]')?.children).toHaveLength(2);
  });
  it('slider 로 진행률·시간을 드러내고 탭하면 onSeek', () => {
    const onSeek = vi.fn();
    render(<LiveProgress ratio={0.51} timeLabel="5:20 / 10:30" onSeek={onSeek} />);
    const slider = screen.getByRole('slider', { name: '재생 위치' });
    expect(slider).toHaveAttribute('aria-valuenow', '51');
    expect(slider).toHaveAttribute('aria-valuetext', '5:20 / 10:30');
    // jsdom(테스트용 가짜 브라우저)은 요소 크기를 0 으로 주므로 넘어간 비율 값은 보지 않고, 불렸는지만 본다
    fireEvent.click(slider, { clientX: 0 });
    expect(onSeek).toHaveBeenCalled();
    expect(css).toContain('var(--touch-target)');
  });

  // tone: 기본(onVideo)은 영상 위 흰색, onLight 는 밝은 화면 배경(세로 녹화본) 위라 토큰 색(글자·트랙·손잡이·포커스 테두리)을 쓴다
  it('tone 2종이 클래스로 드러나고, onLight 는 흰색 원본색 대신 토큰 색을 쓴다', () => {
    const { container, unmount } = render(<LiveProgress ratio={0.5} timeLabel="5:20 / 10:30" />);
    expect(container.firstChild.className).toContain('onVideo');
    unmount();
    const { container: light } = render(<LiveProgress tone="onLight" ratio={0.5} timeLabel="5:20 / 10:30" />);
    expect(light.firstChild.className).toContain('onLight');
    // .onLight 로 시작하는 규칙(트랙·손잡이·시간·포커스)은 모두 --color-* 토큰이고 흰색(--raw-on-video)·흰 32% 트랙을 쓰지 않는다
    // 구간 틈의 transparent 규칙은 색을 칠하지 않는다. 실제 색을 칠하는 선택자만 검사한다.
    const lightRules = [...css.matchAll(/\.onLight \.(?:track(?::focus-visible)?|knob|time|segment)\s*\{([^}]*)\}/g)].map((m) => m[1]);
    expect(lightRules.length).toBeGreaterThanOrEqual(4);
    for (const body of lightRules) {
      expect(body).toMatch(/var\(--color-/);
      expect(body).not.toMatch(/--raw-on-video|--raw-progress-track/);
    }
  });

  // pressed 를 주면 aria-pressed="true" 로 드러나고 클릭이 전달되는지 본다
  it('IconButton 은 라벨과 aria-pressed', () => {
    const onClick = vi.fn();
    render(<IconButton icon={<svg />} label="음소거" pressed onClick={onClick} />);
    const btn = screen.getByRole('button', { name: '음소거' });
    expect(btn).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
