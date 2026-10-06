// Figma: Service/LiveLandscape (2165:2222) — 852×480 최신 가로 화면. (구버전 · 회전 프레임 · 사용 안 함) 은 변환하지 않음.
// 전체 화면 영상 + ManagerIdentity(좌상) + PillControl 캡처/종료(우상) + CircleControl 15초 뒤로/일시정지/15초 앞으로(가운데) + 진행 막대·시간(하단) + 음소거·전체화면(우하)
// 852×480 무대를 화면에 맞춰 축소하고, 세로 화면에서는 90° 돌린다 (PROGRESS.md 판단 항목). 스트림·탐색은 useLiveSession
// (B5) 15초 뒤로/앞으로·일시정지·진행 막대는 녹화본(?replay=1) 에서만 그리고, 실시간에는 그리지 않는다. 음소거는 비활성(§13-23)
// 들어오는 길: 세로 화면(ServiceLivePortrait)의 '전체 화면' 버튼이 같은 주소 쿼리(?booking=…&replay=…)를 붙여
// /service/live/landscape 로 바꾼다.
// '전체 화면 닫기' 는 같은 쿼리로 세로 화면으로 돌아가고, '종료' 는 이 기기의 시청만 바로 끝내고(leave) 내 예약으로 간다.
import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { VideoSurface, ManagerIdentity, LivePillControl, LiveCircleControl, LiveProgress, IconButton, MuteSpeakerIcon, FullscreenIcon, ScreenError, ASSETS, cx } from '@ansimclean/ui';
import { useLiveSession } from '../../live/useLiveSession.js';
import { LIVE_ERROR_COPY } from '../../api/mock.js';
import styles from './ServiceLiveLandscape.module.css';

// Figma 가로 프레임 크기. 화면은 이 크기로 그린 뒤 통째로 크기를 맞추고 회전한다 (CSS 의 .stage 와 같은 값)
const STAGE = { width: 852, height: 480 };

/**
 * 창 크기에 맞춰 852×480 무대를 몇 배로 그릴지(scale — 폰에서는 1보다 작고 큰 창에서는 커진다), 돌릴지(rotate)를 계산하는 훅.
 * 세로로 긴 창이면 가로·세로를 바꿔서 계산하고 90° 돌린다. 창 크기가 바뀌면(resize) 다시 계산한다.
 * @returns {{ rotate: boolean, scale: number }}
 */
function useStageTransform() {
  const [t, setT] = useState({ rotate: false, scale: 1 });
  useEffect(() => {
    const update = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const rotate = h > w; // 세로 화면이면 무대를 90° 돌린다
      const vw = rotate ? h : w;
      const vh = rotate ? w : h;
      // 가로·세로 중 더 빠듯한 쪽에 맞춘다 — 무대 전체가 잘리지 않고 화면 안에 들어간다
      setT({ rotate, scale: Math.min(vw / STAGE.width, vh / STAGE.height) });
    };
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);
  return t;
}

/**
 * 라이브·녹화본 가로(전체 화면) 시청 화면. 주소의 ?booking=예약 id 와 ?replay=1(녹화본) 을 읽어 useLiveSession 에 넘긴다.
 * 부작용: 캡처(PNG 내려받기), 종료 시 세션에서 나가고 /reservations 로 이동.
 */
export default function ServiceLiveLandscape() {
  const navigate = useNavigate();
  const { search } = useLocation(); // '전체 화면 닫기' 때 세로 화면에 그대로 넘길 쿼리 문자열 (?booking=…&replay=…)
  const [params] = useSearchParams();
  // 훅은 조건문(아래 early return)보다 먼저 불러야 해서(React 규칙) booking 이 없어도 일단 부르고 아래에서 돌려보낸다
  const { session, mode, error, videoRef, muted, capture, seekRatio, back15, forward15, togglePause, status, leave, retry } = useLiveSession(params.get('booking') ?? undefined, { replay: params.get('replay') === '1' });
  const { rotate, scale } = useStageTransform();
  const isLive = mode === 'live' && status === 'live'; // LIVE 배지는 실시간 모드이면서 지금 영상이 나오는 중일 때만
  // 매니저 사진: 공용 UI 의 ASSETS 에 같은 키가 있으면 그 이미지, 없으면 받은 값을 이미지 주소로 쓴다
  const avatar = ASSETS[session?.manager?.avatar] ?? { src: session?.manager?.avatar };

  if (!params.get('booking')) return <Navigate to="/reservations" replace />; // 예약 없이 열린 화면(딥링크·뒤로가기) — 가짜 세션 대신 내 예약으로 (검증 검토)
  // 세션 조회부터 실패해 보여줄 것이 없을 때만 오류 화면. 세션이 있는 상태의 오류(연결·캡처 실패)는 영상 위 한 줄 문구로 보인다
  if (status === 'error' && !session) return <ScreenError error={error} onRetry={retry} onBack={() => navigate(-1)} />;

  // '종료': 유예 없이 바로 세션에서 나간 뒤(leave) 내 예약으로
  const exit = () => {
    leave();
    navigate('/reservations');
  };

  return (
    <div className={styles.viewport}>
      {/* 배율은 창마다 달라 CSS 변수(--stage-scale)로 넘기고, 실제 transform 은 CSS 모듈이 정한다 */}
      <div className={cx(styles.stage, rotate && styles.rotated)} style={{ '--stage-scale': scale }}>
        <VideoSurface ref={videoRef} muted={muted} variant="landscape" className={styles.video}>
          {/* 세션을 받기 전에는 영상 틀만 보이고, 받은 뒤에 오버레이(매니저 정보·컨트롤)를 얹는다 */}
          {session && (
            <>
              <div className={styles.identity}>
                <ManagerIdentity variant="onVideo" liveBadge={isLive} avatar={avatar} name={session.manager.name} description={session.manager.description} />
                {error && (
                  <p role="alert" className={cx('text-body-xs', styles.liveError)}>
                    {error.message || LIVE_ERROR_COPY}
                  </p>
                )}
              </div>
              <div className={styles.pills}>
                <LivePillControl type="capture" onClick={capture} />
                <LivePillControl type="exit" onClick={exit} />
              </div>
              {mode === 'replay' && ( // 탐색·재생/멈춤·진행 막대는 녹화본에만 — 실시간에는 아무것도 하지 않던 컨트롤을 그리지 않는다 (검증 검토)
                <>
                  <div className={styles.center}>
                    <LiveCircleControl type="back15" onClick={back15} />
                    <LiveCircleControl type={status === 'paused' ? 'play' : 'pause'} onClick={togglePause} />
                    <LiveCircleControl type="forward15" onClick={forward15} />
                  </div>
                  <div className={styles.progress}>
                    <LiveProgress ratio={session.playback.ratio} timeLabel={`${session.playback.currentLabel} / ${session.playback.totalLabel}`} onSeek={seekRatio} />
                  </div>
                </>
              )}
              {/* 전체 화면 닫기는 replace 로 이동 — 방문 기록에 가로 화면이 쌓이지 않아 뒤로가기가 세로↔가로를 오가지 않는다 */}
              <div className={styles.bottomRight}>
                <IconButton tone="onVideo" icon={<MuteSpeakerIcon />} label="음소거" pressed={muted} disabled />
                <IconButton tone="onVideo" icon={<FullscreenIcon />} label="전체 화면 닫기" onClick={() => navigate(`/service/live${search}`, { replace: true })} />
              </div>
            </>
          )}
        </VideoSurface>
      </div>
    </div>
  );
}
