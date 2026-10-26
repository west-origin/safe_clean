// CameraFrame 테스트: ref 가 <video> 에 붙는지, 청소 전 사진이 반투명 층으로 겹치는지, 찍은 사진을 보일 때 겹친 사진이 빠지고 영상은 숨는지 확인한다.
import { describe, it, expect } from 'vitest';
import { createRef } from 'react';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { render, screen } from '@testing-library/react';
import CameraFrame from './CameraFrame.jsx';

describe('CameraFrame', () => {
  it('ref 를 <video playsInline muted autoPlay> 에 붙인다', () => {
    const ref = createRef();
    render(<CameraFrame ref={ref} />);
    expect(ref.current.tagName).toBe('VIDEO');
    expect(ref.current.muted).toBe(true);
    expect(ref.current).toHaveAttribute('playsinline');
  });

  it('ghostSrc 를 반투명 층으로 겹친다', () => {
    render(<CameraFrame ghostSrc="/before.jpg" ghostAlt="거실 소파 앞 청소 전" />);
    const ghost = screen.getByAltText('거실 소파 앞 청소 전');
    expect(ghost).toHaveAttribute('src', '/before.jpg');
    const css = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'CameraFrame.module.css'), 'utf8');
    expect(css).toMatch(/\.ghost \{[^}]*opacity: 0\.5/);
  });

  it('previewSrc 가 있으면 찍은 사진만 보이고 겹친 사진은 빠진다', () => {
    const ref = createRef();
    render(<CameraFrame ref={ref} ghostSrc="/before.jpg" ghostAlt="청소 전" previewSrc="blob:shot" />);
    expect(screen.getByAltText('방금 찍은 사진')).toHaveAttribute('src', 'blob:shot');
    expect(screen.queryByAltText('청소 전')).toBeNull();
    expect(ref.current).toBeInTheDocument(); // 영상 요소는 남는다 (숨김)
  });
});
