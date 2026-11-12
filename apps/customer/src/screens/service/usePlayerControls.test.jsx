import { afterEach, it, expect, vi } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { usePlayerControls, PLAYER_CONTROLS_HIDE_MS } from './usePlayerControls.js';
function Harness() {
  const { visible, reveal, hold } = usePlayerControls();
  return <><output>{visible ? '표시' : '숨김'}</output><button onClick={reveal}>터치</button><button onPointerDown={() => hold('drag', true)} onPointerUp={() => hold('drag', false)}>볼륨 드래그</button></>;
}
afterEach(() => vi.useRealTimers());
it('터치 후 3초에 숨기고 다시 움직이면 시간을 새로 센다', () => {
  vi.useFakeTimers();
  render(<Harness />);
  expect(screen.getByText('숨김')).toBeInTheDocument();
  fireEvent.click(screen.getByText('터치'));
  act(() => vi.advanceTimersByTime(2000));
  fireEvent.click(screen.getByText('터치'));
  act(() => vi.advanceTimersByTime(2000));
  expect(screen.getByText('표시')).toBeInTheDocument();
  act(() => vi.advanceTimersByTime(PLAYER_CONTROLS_HIDE_MS - 2000));
  expect(screen.getByText('숨김')).toBeInTheDocument();
});
it('볼륨을 드래그하는 동안 유지하고 놓은 뒤 다시 3초를 센다', () => {
  vi.useFakeTimers();
  render(<Harness />);
  fireEvent.pointerDown(screen.getByText('볼륨 드래그'));
  act(() => vi.advanceTimersByTime(10000));
  expect(screen.getByText('표시')).toBeInTheDocument();
  fireEvent.pointerUp(screen.getByText('볼륨 드래그'));
  act(() => vi.advanceTimersByTime(PLAYER_CONTROLS_HIDE_MS));
  expect(screen.getByText('숨김')).toBeInTheDocument();
});
