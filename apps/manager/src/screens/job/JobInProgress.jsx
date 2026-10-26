// 실시간 청소 진행 화면. 서버 photo phase가 live일 때만 입장/송출한다.
// 체크 완료는 서버에 저장하고, 하단 종료 버튼은 종료 의도 저장 → 라이브 종료 → 필수 후 사진 순서다.
// 일시정지는 기존 영상 차단 → 작업 상태 변경 순서와 실패 복구를 유지한다.
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, Card, Button, StatusBadge, CameraStatusItem, ChecklistItem, ProgressBar, Sheet, VideoSurface, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError, useElapsed } from '@ansimclean/ui';
import { getActiveJob, setChecklistItem, getPauseReasons, pauseJob, getJobPhotos, finishJobVideo } from '../../api/manager.js';
import { LIVE_STATE_COPY } from '../../api/mock.js';
import { useLiveSession } from '../../live/useLiveSession.js';
import ReasonList from '../call/ReasonList.jsx';
import styles from './JobInProgress.module.css';

// 서버가 멈춘 채라고 해서 다시 띄운 시트에도 실패 문구를 보이기 위한 표시 (ActionError 는 error 가 있을 때만 그린다 — 문구는 message 로)
const HELD_NOTICE = { message: '' };


// 청소 진행 화면. 라이브 상태는 useLiveSession(liveClient 가 3초마다 서버에 묻는다)에서, 작업 정보는 useAsync 로 화면을 열 때 받는다.
// 하단 버튼: '촬영 일시정지'(사유 시트) · '청소 완료'(확인 시트)
export default function JobInProgress() {
  const navigate = useNavigate();
  // 훅 결과를 풀어 쓴다: 객체째 두면 videoRef 때문에 lint(react-hooks/refs)가 다른 필드 읽기까지 ref 접근으로 본다
  const { status: liveStatus, jobId: liveJobId, cameraBusy, recordingStatus, recordingStartFailed, reconnecting, held: liveHeld, error: liveError, videoRef, start: liveStart, pause: livePause, resume: liveResume, stop: liveStop } = useLiveSession();
  const { data: job, error, reload, refresh, setData: setJob } = useAsync(() => getActiveJob(), []);
  const jobId = job?.id;
  const elapsed = useElapsed(job?.startedAt, job?.pausedSeconds, job?.elapsed, job?.status === 'paused'); // 라이브가 아니라 작업 상태로 — 영상만 막히고 작업 일시정지가 실패하면 시계가 화면을 연 시점으로 되돌아가 멈췄다 (자체 점검 #43)
  const { data: photoList, error: photoError, reload: reloadPhotos } = useAsync(() => (jobId ? getJobPhotos(jobId) : Promise.resolve(null)), [jobId]);
  const phase = photoList?.phase;
  useEffect(() => {
    if (phase === 'before') navigate('/job/photos/before', { replace: true });
    if (phase === 'after') navigate('/job/photos/after', { replace: true });
  }, [phase, navigate]);
  // 라이브 세션을 서버의 진행 중 작업에 맞춘다
  useEffect(() => {
    // 재진입(앱 재실행·새로고침): 세션이 비어 있거나(idle) 다른 작업이면 서버의 작업 id 로 다시 붙인다.
    // ended 는 이 화면에서 청소를 완료한 직후라 다시 붙지 않는다(끝난 작업에 start 를 보내면 409). error 는 liveClient 가 스스로 재시도한다
    // (단 서버가 409·404 로 거절한 start 는 liveClient 도 다시 보내지 않아 error 로 남는다)
    if (jobId && phase === 'live' && (liveJobId !== jobId || liveStatus === 'idle')) liveStart(jobId);
  }, [jobId, phase, liveJobId, liveStatus, liveStart]);
  useEffect(() => {
    if (job?.status === 'paused') navigate('/job/paused', { replace: true }); // 작업이 이미 일시정지 — 진행 중 화면에 머물면 재개할 길이 없다
  }, [job?.status, navigate]);
  const [reason, setReason] = useState(null); // 사유 목록은 시트 안 ReasonList 가 열릴 때 받는다 — 진행 중 화면이 카탈로그 조회에 가려지지 않게 (검증 검토)
  // 시트 두 개의 열림: 일시정지 사유 시트 / 청소 완료 확인 시트
  const [sheetOpen, setSheetOpen] = useState(false);
  const finishVideo = useAction(async () => {
    await finishJobVideo(job.id);
    await liveStop(job.id);
    navigate('/job/photos/after', { replace: true });
  });

  const [videoBlocked, setVideoBlocked] = useState(false); // ① 성공 뒤 ② 실패 — 영상은 이미 막혀 있다(안전한 쪽). 문구가 반대로 나가지 않게
  // 이 시트에서 ① 을 한 번이라도 보냈는가 — 응답을 잃어 결과를 모를 수도 있다. 닫을 때 촬영을 다시 시작할지 정한다 (시도마다 지우지 않는다)
  const pauseTried = useRef(false);
  // 촬영 일시정지 (순서는 파일 머리 설명): ① 영상 차단 → ② 작업 상태 → ③ 일시정지 화면. 어느 단계든 실패하면 시트에 문구
  const pause = useAction(async () => {
    setVideoBlocked(false); // 지난 시도의 ① 결과가 이번 시도의 문구에 섞이지 않게 (검수 3차)
    pauseTried.current = true;
    setSheetOpen(true); // 서버가 다시 띄운 시트(heldPrompt)에서 눌렀어도 진행 중에 시트가 닫히지 않게
    await livePause(job.id); // ① 영상이 실제로 막힌 뒤에만 다음으로 (실패하면 throw → 시트에 문구)
    // 바디캠이 안 붙은 채 ① 이 200 이면 서버는 'mute 요청' 과 '매니저가 멈춤' 표시를 남긴다(LiveService.pause). ② 가 닿기 전에 바디캠이 붙어도
    // 서버가 새 트랙을 막는다(cameraAttached). 막았는지 아직 확인하지 못했으면 ② 는 409 로 거절되고, 다시 누르면 ① 이 확인한 뒤 통과한다
    setVideoBlocked(true);
    await pauseJob(job.id, reason); // ② 작업 상태 (실패해도 ① 은 멱등이라 다시 누르면 통과)
    setSheetOpen(false);
    navigate('/job/paused'); // ③
  });
  // 서버가 '매니저가 영상을 멈춘 채' 라고 하는데 작업은 진행 중이다(② 실패, 닫을 때의 재개가 실패, 앱이 꺼졌다 다시 열림 등).
  // 이 화면에는 재개 버튼이 없어 그대로 두면 작업 끝까지 영상이 막힌다 — '영상은 중단됐지만 작업 상태를 바꾸지 못했어요' 시트를 다시 띄워
  // 다시 시도(② 로 일시정지 화면) 또는 닫기(촬영 다시 시작) 중 하나를 고르게 한다. 문구는 기존 시트 그대로
  const heldPrompt = liveHeld && job?.status === 'inProgress' && liveJobId === job?.id && !pause.loading;
  const blocked = videoBlocked || heldPrompt; // 시트 문구: 영상은 막혔다 쪽
  // 닫기 = 촬영 다시 시작. 실패하면 서버 문구를 시트에 두고(서버가 아직 멈춘 채라 시트도 남는다) 다시 닫으면 또 시도한다
  const resumeVideo = useAction(async () => {
    await liveResume(job.id); // 서버가 작업이 실제로 일시정지됐으면(응답만 잃었으면) 거절한다 — 아래 refresh 가 일시정지 화면으로 보낸다
  });
  // 일시정지 시트 닫기. Sheet 의 ESC·배경 탭·핸들 탭도 모두 이 함수로 온다
  const closeSheet = () => {
    // ① 을 보낸 뒤 닫으면 = 일시정지를 그만둔 것 — 영상을 다시 켠다. ① 이 '멈췄다' 였으면 서버는 매니저가 재개할 때까지 막아 두고
    // (바디캠이 끊겼다 다시 붙어도, live_sessions.video_held), ① 의 응답을 잃었으면 결과를 모르므로 역시 재개를 보낸다(이미 나가고 있으면 아무 일 없다)
    if ((blocked || pauseTried.current) && job) {
      resumeVideo.run();
      refresh().catch(() => {}); // ② 가 서버에선 성공했을 수 있다 — 상태를 다시 받아 일시정지 화면으로 (자체 점검 #43). 화면을 비우지 않고 조용히
    }
    setSheetOpen(false);
    pause.reset(); // 다시 열 때 이전 실패 문구가 남지 않게
    setVideoBlocked(false);
    pauseTried.current = false;
  };
  // 체크리스트: 서버가 진실. 실패하면 문구로 알리고 값은 그대로 (§10 useAction)
  const toggle = useAction(async (itemId, done) => {
    await setChecklistItem(job.id, itemId, done);
    setJob((prev) => ({
      ...prev,
      checklist: prev.checklist.map((g) => ({ ...g, items: g.items.map((i) => (i.id === itemId ? { ...i, done } : i)) })),
    }));
  });


  // 오류 화면의 '다시 시도': 작업 정보를 처음부터 다시 받는다
  const reloadAll = () => {
    reload();
  };
  if (error) return <ScreenError error={error} onRetry={reloadAll} onBack={() => navigate(-1)} />;
  if (!job) return <ScreenLoading />;
  if (photoError) return <ScreenError error={photoError} onRetry={reloadPhotos} />;
  if (!photoList || phase !== 'live') return <ScreenLoading />;

  // 라이브 문구: 바디캠을 껐다 켜야 하면(cameraBusy) 그 안내, 휴대폰 송출이 잠깐 끊겨 다시 붙는 중이면(reconnecting) 그 안내, 아니면 상태별 문구. 문구 표에 없는 상태는 '카메라 연결 대기 중'
  const liveText = cameraBusy ? LIVE_STATE_COPY.cameraBusy : reconnecting ? LIVE_STATE_COPY.reconnecting : (LIVE_STATE_COPY[liveStatus] ?? LIVE_STATE_COPY.connecting);
  const since = `${job.sinceLabel ?? job.since} · ${liveText}`; // 서버 sinceLabel('14:02부터 진행') + 3초 폴링의 라이브 상태 문구
  // 배지는 라이브 상태를 따른다: live → 실시간, paused → 일시정지, 그 밖의 상태(연결 대기·오류 등)는 진행 중
  const badge = liveStatus === 'live' ? 'live' : liveStatus === 'paused' ? 'paused' : 'inProgress';
  // 진행률: 모든 그룹의 항목을 한 줄로 펼쳐 완료 수를 센다
  const allItems = job.checklist.flatMap((g) => g.items);
  const doneCount = allItems.filter((i) => i.done).length;
  return (
    <ScreenLayout
      gap="ml"
      stickyCta={
        <>
          <Button variant="secondary" onClick={() => setSheetOpen(true)}>
            촬영 일시정지
          </Button>
          <ActionError error={finishVideo.error} /><Button loading={finishVideo.loading} onClick={finishVideo.run}>실시간 영상 종료</Button>
        </>
      }
    >
      {/* 상단 카드: 제목·배지 → 경과 시간 → 시작 시각 · 라이브 문구 → (있으면) 라이브 오류 → 바디캠 미리보기 */}
      <Card tone="live" gap="md">
        <div className={styles.row}>
          <h1 className="text-heading-s">{job.title}</h1>
          <StatusBadge status={badge} />
        </div>
        <p className="text-heading-xl">{elapsed}</p>
        <p className={cx('text-body-s', styles.secondary)}>{since}</p>
        {recordingStatus === 'failed' && (
          <p role="status" className={cx('text-body-s', styles.warning)}>
            {recordingStartFailed ? '녹화를 시작하지 못해 다시 시도하고 있어요.' : '영상 녹화에 문제가 생겼어요.'} 영상 종료 후 사진 촬영은 계속할 수 있어요.
          </p>
        )}
        {/* liveClient 가 남긴 라이브 오류. role="status" 는 스크린리더가 하던 말을 끊지 않고 읽어 준다 */}
        {liveError && (
          <p role="status" className={cx('text-body-s', styles.warning)}>
            {liveError.message || LIVE_STATE_COPY.error}
          </p>
        )}
        {/* 바디캠 미리보기 <video>. videoRef(liveClient.attachVideo)로 liveClient 가 영상 트랙을 붙인다 */}
        <VideoSurface ref={videoRef} className={styles.preview} />
      </Card>

      {/* 촬영 상태: 서버 equipment 행을 한 줄씩 (항목명·상태 문구·ok/issue) */}
      <section className={styles.block}>
        <h2 className="text-title-card">촬영 상태</h2>
        <Card padding="list" gap="none" divided>
          {job.equipment.map((item) => (
            <CameraStatusItem key={item.label} status={item.status} label={item.label} value={item.value} />
          ))}
        </Card>
      </section>

      <Card tone="warningMuted" padding="note" gap="sm">
        <h2 className={cx('text-title-small', styles.warning)}>고객 요청사항</h2>
        <ul className={cx('text-body-default', styles.bullets)}>
          {job.requests.map((r) => (
            <li key={r}>• {r}</li>
          ))}
        </ul>
      </Card>

      <section className={styles.checklist}>
        <div className={styles.progress}>
          <div className={styles.row}>
            <h2 className="text-title-card">청소 체크리스트</h2>
            <span className={cx('text-title-small', styles.brand)}>
              {allItems.length}개 중 {doneCount}개 완료
            </span>
          </div>
          <ProgressBar value={doneCount} max={allItems.length} label="청소 체크리스트" />
        </div>
        {/* 그룹별 체크 항목. 저장 요청이 진행 중(toggle.loading)이면 모든 항목이 잠겨 한 번에 하나씩 저장된다 */}
        {job.checklist.map((group) => (
          <div key={group.group} className={styles.group}>
            <h3 className="text-title-card">{group.group}</h3>
            {group.items.map((item) => (
              <ChecklistItem key={item.id} label={item.label} checked={item.done} disabled={toggle.loading} onChange={(v) => toggle.run(item.id, v)} />
            ))}
          </div>
        ))}
        <ActionError error={toggle.error} message={LIVE_STATE_COPY.checklistFailed} />
      </section>

      {/* 일시정지 시트: 사유는 ReasonList 가 시트를 열 때 받고, 목록이 오면 첫 사유를 기본 선택한다(onLoaded) */}
      <Sheet open={sheetOpen || heldPrompt} onClose={closeSheet} title="촬영을 잠시 중단하시겠어요?">
        <ReasonList open={sheetOpen || heldPrompt} load={getPauseReasons} ariaLabel="중단 사유" value={reason} onChange={setReason} onLoaded={(list) => setReason((r) => r ?? list[0])} />
        {/* 실패 문구는 어느 단계에서 실패했는지로 고른다 — ① 실패면 '영상이 계속 전송', ② 실패면(또는 서버가 멈춘 채라고 하면) '영상은 중단됐지만' */}
        <ActionError error={pause.error ?? (heldPrompt ? HELD_NOTICE : null)} message={blocked ? LIVE_STATE_COPY.pauseJobFailed : LIVE_STATE_COPY.pauseVideoFailed} />
        <ActionError error={resumeVideo.error} />
        <Button loading={pause.loading} disabled={!reason} onClick={pause.run}>
          촬영 일시정지
        </Button>
      </Sheet>

    </ScreenLayout>
  );
}
