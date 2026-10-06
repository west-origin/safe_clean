import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import LivePillControl from './LivePillControl.jsx';

// LivePillControl 테스트: Capture·Exit 가 Figma 문구 이름의 버튼으로 그려지고 클릭이 onClick 으로 전달되는지 본다.
// 두 번 render 하면 두 버튼이 같은 문서에 함께 있게 되지만 이름이 달라 각각 찾을 수 있다.
describe('LivePillControl', () => {
  it('Capture/Exit 가 Figma 문구로 렌더되고 클릭이 전달된다', () => {
    const onClick = vi.fn();
    render(<LivePillControl type="capture" onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: '화면 캡처' }));
    render(<LivePillControl type="exit" onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: '종료하기' }));
    expect(onClick).toHaveBeenCalledTimes(2);
  });
});
