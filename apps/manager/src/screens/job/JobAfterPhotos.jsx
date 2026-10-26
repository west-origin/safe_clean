// 청소 후 필수 사진. 서버의 종료 단계와 실제 live.stop 성공을 확인한 뒤 장소별 촬영을 연다.
// 모든 필수 장소와 기존 위치의 후 사진이 있어야 완료 확인/요청을 허용하며 서버도 동일 검사한다.
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Button, Card, PhotoPairRow, Sheet, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getActiveJob, getJobPhotos, completeJob } from '../../api/manager.js';
import { useLiveSession } from '../../live/useLiveSession.js';
import { PHOTO_COPY } from '../../api/mock.js';
import styles from './JobAfterPhotos.module.css';

/** 진행 중 작업의 사진 목록 (작업 id 는 서버의 진행 중 작업에서) */
async function loadPhotos() {
  const job = await getActiveJob();
  return { job, ...await getJobPhotos(job.id) };
}

/** 청소 후 사진 화면: 위치별로 같은 각도의 청소 후 사진을 찍게 하고, 다 찍으면 완료 확인으로 보낸다 */
export default function JobAfterPhotos() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(loadPhotos, []);
  const { stop } = useLiveSession();
  const [stopped, setStopped] = useState(false);
  const [stopError, setStopError] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const id = data?.job.id;
  const phase = data?.phase;
  // 앱 재실행/응답 손실에도 종료 API를 재시도한다. 성공 전에는 후 사진으로 진행하지 않는다.
  useEffect(() => {
    if (!id) return;
    if (phase !== 'after') { navigate(phase === 'before' ? '/job/photos/before' : '/job/in-progress', { replace: true }); return; }
    let active = true;
    stop(id).then(() => { if (active) setStopped(true); }, (e) => { if (active) setStopError(e); });
    return () => { active = false; };
  }, [id, phase, stop, navigate]);
  const retry = useAction(async () => { await stop(id); setStopError(null); setStopped(true); });
  const complete = useAction(async () => { await completeJob(id); navigate('/job/complete', { replace: true }); });
  const toProgress = () => navigate('/job/in-progress', { replace: true });
  const navBar = <NavBar title={PHOTO_COPY.afterTitle} onBack={toProgress} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data || phase !== 'after') return <ScreenLoading navBar={navBar} />;
  if (stopError) return <ScreenError error={retry.error ?? stopError} onRetry={retry.run} navBar={navBar} />;
  if (!stopped) return <ScreenLoading navBar={navBar} />;

  const required = data.requiredRooms ?? [];
  const missing = required.filter((room) => !data.photos.some((p) => p.location === room && p.afterUrl)).length;
  const remaining = Math.max(missing, data.photos.filter((p) => !p.afterUrl).length);
  const ready = required.length > 0 && remaining === 0;
  return (
    <ScreenLayout
      navBar={navBar}
      gap="md"
      stickyCta={
        <>
          {remaining > 0 && (
            <p className={cx('text-body-s', styles.remaining)} role="status">
              {remaining}
              {PHOTO_COPY.afterRemaining}
            </p>
          )}
          {/* 완료 확인 시트는 진행 화면에 있다 — 그 화면을 시트가 열린 채로 연다 */}
          <Button disabled={!ready} onClick={() => setConfirm(true)}>
            {PHOTO_COPY.complete}
          </Button>
        </>
      }
    >
      <p className={cx('text-body-s', styles.secondary)}>실시간 영상이 종료되었어요. 각 공간의 전 사진에 맞춰 후 사진을 모두 찍어주세요.</p>
      <Card padding="list" gap="none" divided>
        {data.photos.map((p) => (
          <PhotoPairRow
            key={p.id}
            location={p.location}
            beforeSrc={p.beforeUrl}
            afterSrc={p.afterUrl}
            showAfter
            description={p.afterUrl ? PHOTO_COPY.afterDone : PHOTO_COPY.afterNeeded}
            tone={p.afterUrl ? 'done' : 'needed'}
            actionLabel={PHOTO_COPY.afterAction}
            onClick={() => navigate(`/job/photo?id=${encodeURIComponent(p.id)}`)}
          />
        ))}
      </Card>
      <Sheet open={confirm && ready} onClose={() => setConfirm(false)} title="청소를 완료하시겠어요?">
        <ActionError error={complete.error} />
        <Button variant="secondary" disabled={complete.loading} onClick={() => setConfirm(false)}>사진 더 확인하기</Button>
        <Button loading={complete.loading} onClick={complete.run}>청소 완료</Button>
      </Sheet>
    </ScreenLayout>
  );
}
