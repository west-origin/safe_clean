// Figma: Service/LivePortrait (2167:2238) — 최신 라이브 세로 화면. (구버전 · 사용 안 함) 프레임은 변환하지 않음.
// StatusBar → 헤더(뒤로 · BrandLogo(에셋 필요 → 글자) · 설정) → ManagerRow → VideoCard(오버레이) → 컨트롤 3개 → ReassuranceBanner
// 영상은 useLiveSession 의 videoRef 로만 연결. 전체 화면 → /service/live/landscape, 종료 → /reservations (PROGRESS.md 판단 항목)
// (B5, 결정 §13-23) 바디캠은 영상만 보내므로 소리 설정 시트는 없고 음소거·설정 버튼은 비활성. ?replay=1 이면 녹화본 재생. 상태 문구(카메라 대기·중단·종료)는 서버가 준다
// 들어오는 길 예: 내 예약 카드의 영상 보기(?booking=…)·녹화 영상 보기(&replay=1), 라이브 푸시 탭(pushClient.js).
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, IconButton, LiveBackIcon, SettingsIcon, FullscreenIcon, ManagerIdentity, RealtimePill, VideoSurface, LiveBadge, ViewerChip, CleaningStatusCard, LiveLabeledControl, LiveCircleControl, LiveProgress, ReassuranceBanner, ScreenError, ScreenLoading, ASSETS, cx, Card, usePolling, ChecklistProgressCard, ChecklistRequests, ErrorState } from '@ansimclean/ui';
import { useLiveSession } from '../../live/useLiveSession.js';
import { LIVE_ERROR_COPY, LIVE_CHECKLIST_COPY } from '../../api/mock.js';
import { getLiveChecklist } from '../../api/customer.js';
import { buildChapters, chapterTitleAt } from './timeline.js';
import styles from './ServiceLivePortrait.module.css';

/**
 * 라이브·녹화본 세로 시청 화면. 주소의 ?booking=예약 id 와 ?replay=1(녹화본) 을 읽어 useLiveSession 에 넘긴다.
 * 부작용: 캡처(PNG 내려받기), 종료 시 세션에서 나가고 /reservations 로 이동, 전체 화면 버튼은 가로 화면으로 바꾼다.
 */
export default function ServiceLivePortrait() {
  const navigate = useNavigate();
  const { search } = useLocation(); // 가로 화면으로 갈 때 그대로 붙여 넘길 쿼리 문자열 (?booking=…&replay=…)
  const [params] = useSearchParams();
  const bookingId = params.get('booking') ?? undefined;
  const replay = params.get('replay') === '1';
  // 훅은 조건문(아래 early return)보다 먼저 불러야 해서(React 규칙) booking 이 없어도 일단 부르고 아래에서 돌려보낸다
  const { session, status, mode, error, videoRef, muted, capture, leave, retry, seekRatio, back15, forward15, togglePause } = useLiveSession(bookingId, { replay });
  const readReplay = replay || (session != null && mode === 'replay');
  // PDF 카드만 최신 화면에 추가한다. 숨겨진 화면에서는 usePolling이 간격 조회를 멈춘다.
  const { data: checklist, error: checklistError, reload: retryChecklist } = usePolling(() => bookingId ? getLiveChecklist(bookingId) : Promise.resolve(null), [bookingId, readReplay], { interval: readReplay ? 0 : 5000 });
  const chapters = mode === 'replay' ? buildChapters(checklist?.timeline, session?.playback.totalSeconds ?? 0) : [];
  const chapterTitle = chapterTitleAt(chapters, session?.playback.ratio ?? 0);
  const isLive = mode === 'live' && status === 'live'; // 실시간 모드이면서 지금 영상이 나오는 중일 때만 LIVE 배지·실시간 표시
  const avatar = ASSETS[session?.manager?.avatar] ?? { src: session?.manager?.avatar }; // 서명 URL 이 오면 그대로 (§5.2 avatar = 키 또는 URL)

  // 예약 id 없음 → 내 예약으로, 세션 조회 자체가 실패(네트워크 등) → ScreenError(다시 시도·뒤로), 아직 받는 중 → 로딩 (코드에서 정함)
  if (!params.get('booking')) return <Navigate to="/reservations" replace />; // 예약 없이 열린 화면(딥링크·뒤로가기) — 가짜 세션 대신 내 예약으로 (검증 검토)
  if (status === 'error' && !session) return <ScreenError error={error} onRetry={retry} onBack={() => navigate(-1)} />;
  if (!session) return <ScreenLoading />;

  // '종료': 유예 없이 바로 세션에서 나간 뒤(leave) 내 예약으로
  const exit = () => {
    leave();
    navigate('/reservations');
  };

  return (
    <ScreenLayout noPadding gap="none">
      <header className={styles.header}>
        <IconButton icon={<LiveBackIcon />} label="뒤로가기" onClick={() => navigate(-1)} />
        {/* TODO(asset): 에셋 필요 — BrandLogo (2167:2250) 로고 이미지. 지금은 글자 */}
        <span className={cx('text-title-card', styles.brand)}>안심클린</span>
        {/* Figma 의 설정 버튼. 연결된 화면이 없고 소리 설정도 없어(§13-23) 비활성 (코드에서 정함) */}
        <IconButton icon={<SettingsIcon />} label="설정" disabled />
      </header>

      {session && (
        <>
          <div className={styles.managerRow}>
            <ManagerIdentity avatar={avatar} name={session.manager.name} description={session.manager.description} trailing={isLive ? <RealtimePill /> : undefined} />
          </div>

          <div className={styles.videoWrap}>
            <VideoSurface ref={videoRef} muted={muted}>
              {/* 좌상: LIVE 배지(영상이 나오는 중일 때) · 시청자 수(실시간 모드일 때). 녹화본에는 둘 다 없다 */}
              <div className={styles.topLeft}>
                {isLive && <LiveBadge />}
                {mode === 'live' && <ViewerChip count={session.viewers} />}
              </div>
              {/* 우상: 서버가 준 시작 시각 문구, 좌하: 서버가 준 청소 상태 문구 */}
              <span className={cx('text-caption-l-bold', styles.timestamp)}>{session.startedAt}</span>
              <div className={styles.statusCard}>
                <CleaningStatusCard title={session.status.title} description={session.status.description} />
              </div>
              <div className={styles.fullscreen}>
                <IconButton tone="onVideo" icon={<FullscreenIcon size={22} />} label="전체 화면" onClick={() => navigate(`/service/live/landscape${search}`, { replace: true })} />
              </div>
            </VideoSurface>
          </div>

          {/* 세션은 있는데 연결·캡처가 실패했을 때 영상 아래 한 줄 문구. 서버·클라이언트 문구가 없으면 공통 문구 */}
          {error && (
            <p role="alert" className={cx('text-body-s', styles.liveError)}>
              {error.message || LIVE_ERROR_COPY}
            </p>
          )}
          {mode !== 'replay' && checklist?.total > 0 && (
            <div className={styles.checklist}><ChecklistProgressCard checklist={checklist} copy={LIVE_CHECKLIST_COPY} /></div>
          )}
          {checklistError && (
            <div className={styles.checklist}><ErrorState error={checklistError} onRetry={retryChecklist} /></div>
          )}
          {mode === 'replay' && ( // 녹화본: 세로 화면에도 재생/멈춤·15초 탐색·진행 막대 (코드에서 정함 — 자동 재생만 있고 멈출 길이 전체 화면뿐이었다)
            // 영상 아래 흰 화면 배경에 놓이므로 tone="onLight": 진행 막대의 손잡이·시간 글자와 원 버튼의 포커스 테두리가 흰색이면 배경에 묻힌다
            <div className={styles.replayControls}>
              <div className={styles.replayButtons}>
                <LiveCircleControl type="back15" tone="onLight" onClick={back15} />
                <LiveCircleControl type={status === 'paused' ? 'play' : 'pause'} tone="onLight" onClick={togglePause} />
                <LiveCircleControl type="forward15" tone="onLight" onClick={forward15} />
              </div>
              <LiveProgress tone="onLight" ratio={session.playback.ratio} timeLabel={`${session.playback.currentLabel} / ${session.playback.totalLabel}`} onSeek={seekRatio} chapters={chapters} chapterTitle={chapterTitle} />
            </div>
          )}
          {mode === 'replay' && checklist && (
            <div className={styles.checklist}><Card padding="sm" gap="sm"><ChecklistRequests copy={LIVE_CHECKLIST_COPY} requests={checklist.requests} /></Card></div>
          )}
          <div className={styles.controls}>
            <LiveLabeledControl type="capture" onClick={capture} />
            <LiveLabeledControl type="exit" onClick={exit} />
            <LiveLabeledControl type="mute" disabled />
          </div>

          {mode !== 'replay' && ( // 청소가 끝난 녹화본에 '청소하고 있어요' 배너를 얹지 않는다
            <div className={styles.banner}>
              <ReassuranceBanner title={session.reassurance.title} description={session.reassurance.description} />
            </div>
          )}
        </>
      )}

    </ScreenLayout>
  );
}
