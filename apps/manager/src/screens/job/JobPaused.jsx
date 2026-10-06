// Figma: Manager/Job/Paused (2141:2661)
// 프로토타입: 촬영 다시 시작 → Job/InProgress
// 매니저 앱 /job/paused 화면. JobInProgress 가 일시정지를 마쳤거나 작업이 이미 paused 일 때, 홈의 진행 중 카드가 paused 일 때 온다.
// 중단 정보(getPausedJob → GET /manager/jobs/active/pause)의 중단 시각·안내 문구를 그리고, '촬영 다시 시작' 으로 재개한다.
import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, Card, Button, StatusBadge, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getPausedJob, getActiveJob, resumeJob } from '../../api/manager.js';
import { useLiveSession } from '../../live/useLiveSession.js';
import styles from './JobPaused.module.css';

// 촬영 일시정지 화면. 재개 순서와, 이미 재개된 뒤 다시 들어온 경우(일시정지 조회 404)의 복구는 아래 주석에 있다
export default function JobPaused() {
  const navigate = useNavigate();
  const { data: paused, error, reload } = useAsync(() => getPausedJob(), []);
  const { jobId: liveJobId, status: liveStatus, start: liveStart, resume: liveResume } = useLiveSession();
  const jobId = paused?.jobId;
  useEffect(() => {
    // 재진입(앱 재실행): 세션이 비어 있거나(idle) 다른 작업이면 서버의 작업 id 로 다시 붙인다 (resume 은 서버가 준 작업 id 로
    // 부르므로 null 로 가지 않지만, 폴링·프리뷰는 start 를 불러야 다시 살아난다)
    if (jobId && (liveJobId !== jobId || liveStatus === 'idle')) liveStart(jobId);
  }, [jobId, liveJobId, liveStatus, liveStart]);
  // 순서(코드에서 정함): ① 작업 상태를 진행 중으로(resumeJob, 재호출해도 같은 결과) → ② 영상 재개(live.resume). 영상이 나가는데 상태가 "일시정지" 인 순간이 없게.
  // ①이 실패하면 여기 남아 문구를 보여주고 다시 시도한다. ①이 되면 작업은 이미 진행 중이다 — ②가 실패해도 이 '일시정지' 화면에 남기지 않는다:
  // 서버가 재개 요청(unmute_pending)을 남겨 재조정기가 1분 안에 영상을 켜는데 화면만 일시정지라고 말했다 (자체 점검 #42)
  const resume = useAction(async () => {
    await resumeJob(paused.jobId); // 작업 id 는 서버가 준 값
    await liveResume(paused.jobId).catch(() => {});
    navigate('/job/in-progress');
  });
  // ①만 되고 ② 전에 나갔다 오면 서버는 이미 진행 중이라 일시정지 조회가 404 — 같은 조회만 다시 해서는 영영 복구되지 않았다 (재감사 2차 #5).
  // 서버의 지금 작업을 보고 맞춘다: 진행 중이면 영상 재개(②)를 한 번 더 부르고 진행 화면으로, 작업이 없으면 홈으로
  const alreadyResumed = error?.code === 'NOT_FOUND';
  useEffect(() => {
    if (!alreadyResumed) return undefined;
    // cancelled: 화면이 사라지거나 효과가 다시 돌면 정리 함수가 true 로 바꿔, 늦게 끝난 요청 뒤에 화면을 옮기지 않게 한다
    let cancelled = false;
    (async () => {
      const active = await getActiveJob().catch(() => null);
      if (cancelled) return;
      if (!active) {
        navigate('/', { replace: true });
        return;
      }
      await liveStart(active.id).catch(() => {}); // 앱을 다시 연 직후라 세션이 비어 있다 — 폴링·프리뷰부터 다시 붙인다 (같은 작업이면 그대로, 자체 점검 #41)
      // 작업 재개(resumeJob)가 서버에 닿았으면 '멈춤' 표시(video_held)도 풀렸으므로, 이 요청을 잃어도 재조정기가 영상을 다시 켠다
      await liveResume(active.id).catch(() => {}); // 실패해도 재조정기가 1분 안에 맞춘다(작업 진행 중 + 영상 막힘)
      if (!cancelled) navigate('/job/in-progress', { replace: true });
    })();
    return () => {
      cancelled = true;
    };
  }, [alreadyResumed, liveStart, liveResume, navigate]);

  // 복구 중에는 404 오류 화면 대신 로딩을 보여 준다 — 위 효과가 곧 진행 화면이나 홈으로 옮긴다
  if (alreadyResumed) return <ScreenLoading />;
  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!paused) return <ScreenLoading />;

  return (
    <ScreenLayout
      gap="none"
      stickyCta={
        <>
          <ActionError error={resume.error} message="다시 시작하지 못했어요. 잠시 후 다시 시도해주세요." />
          <Button loading={resume.loading} onClick={resume.run}>
            촬영 다시 시작
          </Button>
        </>
      }
    >
      {/* 중단 안내 카드: 제목·배지, 서버 since('14:32부터 중단됨' 형식), 서버 안내 문구(note) */}
      <Card tone="warning" gap="md">
        <div className={styles.row}>
          <h1 className="text-heading-s">촬영 일시정지</h1>
          <StatusBadge status="paused" />
        </div>
        <p className="text-body-m">{paused.since}</p>
        <p className={cx('text-body-s', styles.secondary)}>{paused.note}</p>
      </Card>
    </ScreenLayout>
  );
}
