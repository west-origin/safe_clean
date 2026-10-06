// VideoSurface 테스트: <video> 가 playsInline·autoPlay·muted 로 그려지고, 넘긴 ref 가 그 <video> 를 가리키며,
// children 오버레이가 함께 보이는지 본다. createRef 로 만든 ref 는 마운트된 요소가 ref.current 에 들어온다.
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { createRef } from 'react';
import VideoSurface from './VideoSurface.jsx';

describe('VideoSurface', () => {
  it('<video playsInline muted autoPlay> 를 그리고 ref 로 노출한다', () => {
    const ref = createRef();
    const { container } = render(
      <VideoSurface ref={ref}>
        <span>오버레이</span>
      </VideoSurface>,
    );
    const video = container.querySelector('video');
    expect(ref.current).toBe(video);
    expect(video).toHaveAttribute('playsinline');
    expect(video).toHaveAttribute('autoplay');
    expect(video.muted).toBe(true);
    expect(container).toHaveTextContent('오버레이');
  });
});
