import { Volume1, Volume2, VolumeX } from 'lucide-react';
import { useRef, useState } from 'react';
import { cx } from '@ansimclean/ui';
import styles from './PlayerVolumeControl.module.css';

export default function PlayerVolumeControl({ muted, volume, onToggleMute, onVolumeChange, onInteraction }) {
  const [expanded, setExpanded] = useState(false);
  const dragging = useRef(false);
  const silent = muted || volume === 0;
  const VolumeIcon = silent ? VolumeX : volume > 0.5 ? Volume2 : Volume1;
  const value = silent ? 0 : Math.round(volume * 100);
  const finishDrag = () => { dragging.current = false; onInteraction(false); };
  return <div className={styles.sound}
    onPointerEnter={(e) => { if (e.pointerType === 'mouse') setExpanded(true); }}
    onPointerLeave={() => { if (!dragging.current) setExpanded(false); }}
    onFocusCapture={() => setExpanded(true)}
    onBlurCapture={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) setExpanded(false); }}>
    <button type="button" className={styles.button} aria-label="음소거" aria-pressed={silent}
      onClick={() => { setExpanded(true); onToggleMute(); }}>
      <VolumeIcon size={36} strokeWidth={2} aria-hidden="true" focusable="false" className={styles.icon} />
      <span role="tooltip" className={cx('text-body-xs', styles.tooltip)}>{silent ? '음소거 해제' : '음소거'} (m)</span>
    </button>
    <div className={cx(styles.sliderWrap, expanded && styles.expanded)} aria-hidden={!expanded} inert={!expanded}>
      <input className={styles.slider} type="range" min="0" max="100" step="1" aria-label="볼륨" aria-valuetext={`${value} 퍼센트`}
        value={value} style={{ '--volume-ratio': `${value}%` }}
        onChange={(e) => onVolumeChange(Number(e.target.value) / 100)}
        onPointerDown={(e) => { dragging.current = true; onInteraction(true); e.currentTarget.setPointerCapture?.(e.pointerId); }}
        onPointerUp={finishDrag} onPointerCancel={finishDrag} onLostPointerCapture={() => { if (dragging.current) finishDrag(); }} />
    </div>
  </div>;
}
