// 전/후 사진 촬영. 라이브를 시작하지 않고 휴대폰 카메라만 켠다.
// 전: ?room=등록 공간명, 후: ?id=사진쌍 ID. 후 촬영에는 기존 전 사진을 50% 투명도로 겹친다.
// 촬영 → 미리보기 → 저장 성공 후 각 필수 사진 목록으로 돌아간다.
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, Button, CameraFrame, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getActiveJob, getJobPhotos, addBeforePhoto, addAfterPhoto } from '../../api/manager.js';
import { PHOTO_COPY } from '../../api/mock.js';
import { useLiveSession } from '../../live/useLiveSession.js';
import styles from './JobPhotoCapture.module.css';

/** 화면을 열 때: 진행 중 작업 + 그 작업의 사진 목록 (청소 후 촬영이면 겹칠 청소 전 사진을 여기서 찾는다) */
async function loadJobAndPhotos() {
  const job = await getActiveJob();
  const list = await getJobPhotos(job.id);
  return { job, ...list };
}

/** 청소 전·후 사진 한 장을 찍어 올리는 화면. 주소에 ?id= 가 있으면 그 위치의 청소 후 사진 */
export default function JobPhotoCapture() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const photoId = params.get('id'); // 있으면 청소 후 촬영
  const after = Boolean(photoId);
  const room = params.get('room') ?? '';
  const back = after ? '/job/photos/after' : '/job/photos/before';
  const { data, error, reload } = useAsync(loadJobAndPhotos, []);
  const jobId = data?.job.id;
  const { videoRef, preparePhoto, releasePhoto, capturePhoto } = useLiveSession();
  // 앱 재실행 후에도 사진용 카메라만 켠다. 후 사진을 찍으며 라이브 송출을 다시 열지 않는다.
  useEffect(() => {
    if (!jobId) return;
    preparePhoto();
    return () => releasePhoto();
  }, [jobId, preparePhoto, releasePhoto]);

  const [shot, setShot] = useState(null); // 방금 찍은 사진 { file, url } — 저장 전 확인용
  const [referenceOnly, setReferenceOnly] = useState(false);
  const [loadedReference, setLoadedReference] = useState(null);
  const [failedReference, setFailedReference] = useState(null);
  const [referenceAttempt, setReferenceAttempt] = useState(0);
  // 미리보기 주소(blob:)는 사진을 바꾸거나 화면을 떠날 때 돌려준다 (메모리)
  useEffect(() => () => shot && URL.revokeObjectURL?.(shot.url), [shot]);

  // 촬영하기: 지금 영상 장면을 사진으로. 카메라가 아직 장면을 못 받았으면 안내 문구
  const shoot = useAction(async () => {
    let file;
    try {
      file = await capturePhoto();
    } catch {
      throw new Error(PHOTO_COPY.cameraNotReady);
    }
    setShot({ file, url: URL.createObjectURL?.(file) ?? '' });
  });
  // 저장: 업로드가 성공하면 이전 화면으로. 실패하면 찍은 사진을 그대로 두고 문구 (다시 저장 가능)
  const save = useAction(async () => {
    if (after) await addAfterPhoto(jobId, photoId, shot.file);
    else await addBeforePhoto(jobId, room, shot.file);
    navigate(back, { replace: true });
  });
  // 다시 찍기: 확인 중인 사진과 지난 실패 문구를 지우고 영상으로 돌아간다
  const retake = () => {
    setShot(null);
    save.reset();
    shoot.reset();
  };

  const navBar = <NavBar title={after ? PHOTO_COPY.afterTitle : PHOTO_COPY.beforeTitle} onBack={() => navigate(back, { replace: true })} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const target = after ? data.photos.find((p) => p.id === photoId) : null; // 청소 후 촬영할 위치
  if (after && !target) return <ScreenError error={{ message: PHOTO_COPY.notFound }} navBar={navBar} />;
  if ((!after && (data.phase !== 'before' || !data.requiredRooms?.includes(room))) || (after && data.phase !== 'after'))
    return <ScreenError error={{ message: '현재 촬영 단계와 공간을 확인해주세요.' }} navBar={navBar} />;

  const referenceReady = !after || (target.beforeUrl && loadedReference === target.beforeUrl);
  const referenceFailed = after && (!target.beforeUrl || failedReference === target.beforeUrl);

  return (
    <ScreenLayout
      navBar={navBar}
      gap="md"
      stickyCta={
        shot ? (
          <>
            <ActionError error={save.error} fallback={PHOTO_COPY.saveFailed} />
            <Button variant="secondary" disabled={save.loading} onClick={retake}>
              {PHOTO_COPY.retake}
            </Button>
            <Button loading={save.loading} onClick={save.run}>
              {PHOTO_COPY.save}
            </Button>
          </>
        ) : (
          <>
            <ActionError error={shoot.error} />
            <Button loading={shoot.loading} disabled={!referenceReady || referenceOnly} onClick={shoot.run}>
              {PHOTO_COPY.shoot}
            </Button>
          </>
        )
      }
    >
      <header className={styles.head}>
        {/* 청소 후: 어느 위치를 찍는지 제목으로. 청소 전: 위치 이름을 아래 입력칸에 적는다 */}
        <h1 className="text-heading-s">{after ? target.location : room}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{after ? PHOTO_COPY.afterGuide : PHOTO_COPY.beforeGuide}</p>
      </header>
      {/* 카메라 영상 + (청소 후) 반투명 청소 전 사진. 찍은 뒤에는 찍은 사진만 보인다 */}
      {after && !shot && (
        <>
          {referenceFailed ? (
            <>
              <p role="alert">청소 전 사진을 불러오지 못했어요. 다시 불러온 뒤 구도를 맞춰주세요.</p>
              <Button variant="secondary" onClick={() => { setFailedReference(null); setLoadedReference(null); setReferenceAttempt((n) => n + 1); reload(); }}>전 사진 다시 불러오기</Button>
            </>
          ) : (
            <p role="status">{referenceReady ? (referenceOnly ? '청소 전 사진 원본이에요. 카메라와 겹쳐서 구도를 맞춰주세요.' : '청소 전 사진을 반투명하게 겹쳐 표시하고 있어요.') : '청소 전 사진을 불러오는 중이에요.'}</p>
          )}
          <Button variant="secondary" disabled={!referenceReady} aria-pressed={referenceOnly} onClick={() => setReferenceOnly((value) => !value)}>
            {referenceOnly ? '카메라와 겹쳐 보기' : '청소 전 사진만 보기'}
          </Button>
        </>
      )}
      <CameraFrame key={referenceAttempt} ref={videoRef} ghostSrc={target?.beforeUrl} ghostAlt={target ? `${target.location} 청소 전` : ''} ghostOnly={referenceOnly && !shot} onGhostLoad={() => { setLoadedReference(target?.beforeUrl); setFailedReference(null); }} onGhostError={() => setFailedReference(target?.beforeUrl)} previewSrc={shot?.url} />
    </ScreenLayout>
  );
}
