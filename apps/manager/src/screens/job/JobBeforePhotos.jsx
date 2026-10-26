// 청소 전 필수 촬영. 공간 목록은 서버의 작업 체크리스트 스냅샷이며 현장에서 추가할 수 있다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Button, Card, PhotoPairRow, TextField, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getActiveJob, getJobPhotos, confirmBeforePhotos, addJobRoom, deleteJobPhoto } from '../../api/manager.js';

async function load() {
  const job = await getActiveJob();
  return { job, ...await getJobPhotos(job.id) };
}

export default function JobBeforePhotos() {
  const navigate = useNavigate();
  const { data, error, reload, refresh } = useAsync(load, []);
  const [room, setRoom] = useState('');
  const add = useAction(async () => {
    await addJobRoom(data.job.id, room.trim());
    setRoom('');
    await refresh();
  });
  const remove = useAction(async (id) => { await deleteJobPhoto(data.job.id, id); await refresh(); });
  const start = useAction(async () => {
    await confirmBeforePhotos(data.job.id);
    navigate('/job/in-progress', { replace: true });
  });
  const navBar = <NavBar title="청소 전 필수 사진" onBack={() => navigate('/')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const rooms = data.requiredRooms ?? [];
  const remaining = rooms.filter((name) => !data.photos.some((p) => p.location === name)).length;
  const locked = data.phase !== 'before';
  return (
    <ScreenLayout navBar={navBar} gap="md" stickyCta={<>
      <ActionError error={start.error} />
      <p className="text-body-s" role="status">{remaining}곳 촬영이 남았어요</p>
      <Button loading={start.loading} disabled={rooms.length === 0 || remaining > 0 || locked} onClick={start.run}>실시간 영상 시작</Button>
      {locked && <Button onClick={() => navigate(data.phase === 'after' ? '/job/photos/after' : '/job/in-progress', { replace: true })}>진행 중인 단계로 돌아가기</Button>}
    </>}>
      <p className="text-body-s">각 공간에서 전 사진을 한 장씩 찍어주세요. 안방·작은방 등 빠진 공간이 있으면 추가해주세요.</p>
      <Card padding="list" gap="none" divided>
        {rooms.map((name) => {
          const photo = data.photos.find((p) => p.location === name);
          return <PhotoPairRow key={name} location={name} beforeSrc={photo?.beforeUrl}
            description={photo ? '촬영 완료' : '촬영 필요'} tone={photo ? 'done' : 'needed'}
            actionLabel="청소 전 사진 찍기" disabled={locked || remove.loading}
            onClick={!photo ? () => navigate(`/job/photo?room=${encodeURIComponent(name)}`) : undefined}
            onRemove={photo && !locked ? () => remove.run(photo.id) : undefined} />;
        })}
      </Card>
      <ActionError error={remove.error} />
      <TextField label="추가 공간 이름" placeholder="예: 안방, 작은방, 공용 화장실" value={room} onChange={(e) => setRoom(e.target.value)} />
      <ActionError error={add.error} />
      <Button variant="secondary" loading={add.loading} disabled={locked || !room.trim() || rooms.length >= 10} onClick={add.run}>공간 추가</Button>
    </ScreenLayout>
  );
}
