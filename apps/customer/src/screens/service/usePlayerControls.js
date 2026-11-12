import { useEffect, useRef, useState } from 'react';

export const PLAYER_CONTROLS_HIDE_MS = 3000;
export function usePlayerControls() {
  const [visible, setVisible] = useState(false);
  const timer = useRef(null);
  const holds = useRef(new Set());
  const clear = () => { clearTimeout(timer.current); };
  const reveal = () => {
    clear();
    setVisible(true);
    if (!holds.current.size) timer.current = setTimeout(() => setVisible(false), PLAYER_CONTROLS_HIDE_MS);
  };
  const hide = () => { clear(); setVisible(false); };
  const hold = (reason, active) => {
    if (active) { holds.current.add(reason); clear(); setVisible(true); }
    else { holds.current.delete(reason); reveal(); }
  };
  useEffect(() => () => clearTimeout(timer.current), []);
  return { visible, reveal, hide, hold };
}
