import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import LiveLabeledControl from './LiveLabeledControl.jsx';

// LiveLabeledControl 테스트: Figma Type 3종(capture·exit·mute)이 Figma 문구 이름의 버튼으로 그려지고
// 클릭이 onClick 으로 전달되는지 본다. 한 type 을 확인할 때마다 unmount 로 치운다.
describe('LiveLabeledControl', () => {
  it('Capture/Exit/Mute 가 Figma 문구로 렌더되고 클릭이 전달된다', () => {
    const onClick = vi.fn();
    for (const [type, label] of [['capture', '화면 캡처'], ['exit', '종료하기'], ['mute', '음소거']]) {
      const { unmount } = render(<LiveLabeledControl type={type} onClick={onClick} />);
      fireEvent.click(screen.getByRole('button', { name: label }));
      unmount();
    }
    expect(onClick).toHaveBeenCalledTimes(3);
  });
});
