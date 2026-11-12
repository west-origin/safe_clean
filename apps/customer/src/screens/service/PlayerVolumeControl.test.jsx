import { useState } from 'react';
import { it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import PlayerVolumeControl from './PlayerVolumeControl.jsx';
function Harness() {
  const [muted, setMuted] = useState(true);
  const [volume, setVolume] = useState(0.7);
  return <PlayerVolumeControl muted={muted} volume={volume} onToggleMute={() => setMuted((v) => !v)} onVolumeChange={(v) => { setVolume(v); setMuted(v === 0); }} onInteraction={() => {}} />;
}
it('음소거 클릭으로 볼륨 바를 열고 이전 음량과 0 음소거 상태를 반영한다', () => {
  render(<Harness />);
  expect(screen.queryByRole('slider', { name: '볼륨' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '음소거' }));
  const slider = screen.getByRole('slider', { name: '볼륨' });
  expect(slider).toHaveValue('70');
  fireEvent.change(slider, { target: { value: '0' } });
  expect(screen.getByRole('button', { name: '음소거' })).toHaveAttribute('aria-pressed', 'true');
  fireEvent.change(slider, { target: { value: '25' } });
  expect(screen.getByRole('button', { name: '음소거' })).toHaveAttribute('aria-pressed', 'false');
  expect(slider).toHaveAttribute('aria-valuetext', '25 퍼센트');
});
