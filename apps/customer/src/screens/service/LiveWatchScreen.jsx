import { useEffect, useRef, useState } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, ManagerIdentity, IconButton, FullscreenIcon, ASSETS, ScreenLoading, ScreenError, cx } from '@ansimclean/ui';
import { useLiveSession } from '../../live/useLiveSession.js';
import styles from './LiveWatchScreen.module.css';
import LiveCleaningChecklist from './LiveCleaningChecklist.jsx';
import PlayerVolumeControl from './PlayerVolumeControl.jsx';
import { usePlayerControls } from './usePlayerControls.js';

function useFullscreenStage() {
  const [stage, setStage] = useState({ rotate: false, scale: 1 });
  useEffect(() => {
    const resize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const rotate = h > w;
      setStage({ rotate, scale: Math.min((rotate ? h : w) / 852, (rotate ? w : h) / 480) });
    };
    resize();
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, []);
  return stage;
}

export default function LiveWatchScreen({ fullscreen = false }) {
  const navigate = useNavigate();
  const { search } = useLocation();
  const [params] = useSearchParams();
  const bookingId = params.get('booking') ?? undefined;
  const { session, status, error, videoRef, muted, volume, setVolume, toggleMute, retry } = useLiveSession(bookingId);
  const { visible: controlsVisible, reveal, hide, hold } = usePlayerControls();
  const lastPointerAt = useRef(0);
  const stage = useFullscreenStage();
  if (!bookingId) return <Navigate to="/reservations" replace />;
  if (status === 'error' && !session) return <ScreenError error={error} onRetry={retry} onBack={() => navigate(-1)} />;
  if (!session) return <ScreenLoading />;
  const paused = session.live?.state === 'paused' || status === 'paused';
  const avatar = ASSETS[session.manager.avatar] ?? { src: session.manager.avatar };
  const player = (
    <div className={cx(styles.player, !controlsVisible && styles.idle, fullscreen && styles.fullPlayer)}
      onPointerDownCapture={() => { lastPointerAt.current = Date.now(); }}
      onPointerMove={(e) => { if (e.pointerType === 'mouse') reveal(); }}
      onPointerLeave={(e) => { if (e.pointerType === 'mouse') { hold('hover', false); hide(); } }}
      onKeyDown={(e) => {
        if (e.target.tagName === 'INPUT') return;
        if (e.key.toLowerCase() === 'm') { e.preventDefault(); toggleMute(); reveal(); }
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') { e.preventDefault(); setVolume(volume + (e.key === 'ArrowUp' ? 0.05 : -0.05)); reveal(); }
        if (e.key === 'Escape') hide();
      }}>
      <video ref={videoRef} className={styles.video} playsInline autoPlay muted={muted} />
      <button type="button" className={styles.touchSurface} aria-label="영상 컨트롤 표시" onClick={reveal} />
      {paused && <div className={styles.interruption} role="status">
        <p className="text-title-card">촬영이 잠시 중단 됐어요</p>
        <p className="text-body-s">다시 시작되면 영상이 이어져요</p>
      </div>}
      <div className={cx(styles.controls, controlsVisible && styles.controlsVisible)} aria-hidden={!controlsVisible} inert={!controlsVisible}
        onPointerEnter={(e) => { if (e.pointerType === 'mouse') hold('hover', true); }}
        onPointerLeave={(e) => { if (e.pointerType === 'mouse') hold('hover', false); }}
        onClick={reveal}
        onFocusCapture={(e) => { if (Date.now() - lastPointerAt.current > 500 && e.target.matches(':focus-visible')) hold('focus', true); }}
        onBlurCapture={(e) => { if (!e.currentTarget.contains(e.relatedTarget)) hold('focus', false); }}>
        <PlayerVolumeControl muted={muted} volume={volume} onToggleMute={toggleMute} onVolumeChange={setVolume} onInteraction={(active) => hold('drag', active)} />
        <IconButton tone="onVideo" icon={<FullscreenIcon />} label={fullscreen ? '전체 화면 닫기' : '전체 화면'}
          onClick={() => navigate(`/service/live${fullscreen ? '' : '/landscape'}${search}`, { replace: true })} />
      </div>
    </div>
  );
  if (fullscreen) return <div className={styles.viewport}><div className={cx(styles.stage, stage.rotate && styles.rotated)} style={{ '--stage-scale': stage.scale }}>{player}</div></div>;
  return <ScreenLayout noPadding gap="none" navBar={<NavBar onBack={() => navigate(-1)} />}>
    {player}
    <div className={styles.managerRow}>
      <ManagerIdentity avatar={avatar} name={session.manager.name} description={session.manager.description} />
    </div>
    <LiveCleaningChecklist bookingId={bookingId} status={session.live?.state ?? status} />
  </ScreenLayout>;
}
