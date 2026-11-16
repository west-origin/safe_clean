// Manager/Admin/BannerEdit (코드에서 정함, rbac.md §15-22) — Figma 에 없다. 배너 관리 목록의 '배너 올리기'(/admin/banners/new)
// 또는 '수정'(/admin/banners/:id) → 여기. 이미지(1장) · 제목 · 설명 · 게시 시작일·종료일을 정하고 올리거나 저장한다. 기존 배너는 삭제도 된다(확인 시트).
// 저장 순서(기존 배너): 문구·기간 → (이미지를 새로 골랐으면) 이미지 교체. 성공하면 목록으로 돌아가며 토스트. 서버 검사 문구는 버튼 위에 그대로 보인다.
// 기존 ui 프리미티브(ScreenLayout·NavBar·PhotoPicker·TextField·Button·Sheet·ActionError)만 조립한다. 문구는 api/mock.js ADMIN_BANNER_COPY.
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, PhotoPicker, TextField, Button, Sheet, ActionError, ScreenError, ScreenLoading, useAsync, useAction, showToast, cx } from '@ansimclean/ui';
import { getAdminBannerCopy, getAdminBanner, createAdminBanner, updateAdminBanner, replaceAdminBannerImage, deleteAdminBanner } from '../../api/manager.js';
import styles from './admin.module.css';

const TITLE_MAX = 40; // 서버 HomeBanner.TITLE_MAX 와 같다
const DESCRIPTION_MAX = 60; // 서버 HomeBanner.DESCRIPTION_MAX 와 같다

/** 배너 올리기·고치기 (/admin/banners/new · /admin/banners/:id) */
export default function AdminBannerEdit() {
  const navigate = useNavigate();
  const { id } = useParams();
  const isNew = id === 'new';
  const { data, error, reload } = useAsync(() => Promise.all([getAdminBannerCopy(), isNew ? null : getAdminBanner(id)]), [id]);
  const [draft, setDraft] = useState(null); // { title, description, startsOn, endsOn }
  const [file, setFile] = useState(null); // 새로 고른 이미지 (없으면 기존 이미지 그대로)
  const [preview, setPreview] = useState(null); // 미리보기 주소 (고른 파일 또는 서버 이미지)
  const [deleteOpen, setDeleteOpen] = useState(false);
  // 불러온 값으로 입력칸을 한 번 채운다 (렌더 중 한 번만 — effect 로 하면 한 번 더 그린다)
  if (data && !draft) {
    const b = data[1];
    setDraft({ title: b?.title ?? '', description: b?.description ?? '', startsOn: b?.startsOn ?? '', endsOn: b?.endsOn ?? '' });
    setPreview(b?.imageUrl ?? null);
  }
  // 고른 파일의 미리보기 주소는 화면을 떠날 때·다른 파일을 고를 때 풀어 준다
  useEffect(() => () => { if (file && preview?.startsWith('blob:')) URL.revokeObjectURL(preview); }, [file, preview]);

  const save = useAction(async () => {
    if (isNew) {
      await createAdminBanner({ file, ...draft });
    } else {
      await updateAdminBanner(id, draft);
      if (file) await replaceAdminBannerImage(id, file);
    }
    showToast(isNew ? data[0].created : data[0].saved);
    navigate('/admin/banners', { replace: true });
  });
  const remove = useAction(async () => {
    await deleteAdminBanner(id);
    setDeleteOpen(false);
    showToast(data[0].deleted);
    navigate('/admin/banners', { replace: true });
  });

  const title = data ? (isNew ? data[0].newTitle : data[0].editTitle) : '';
  const navBar = <NavBar title={title} onBack={() => navigate('/admin/banners')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data || !draft) return <ScreenLoading navBar={navBar} />;
  const [copy] = data;
  const set = (patch) => setDraft((d) => ({ ...d, ...patch }));
  const ready = Boolean(draft.title.trim()) && Boolean(preview);

  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        <div className={styles.actions}>
          <ActionError error={save.error} />
          <Button chevron={false} loading={save.loading} disabled={!ready} onClick={save.run}>
            {isNew ? copy.submit : copy.save}
          </Button>
        </div>
      }
    >
      <PhotoPicker
        title={copy.imageTitle}
        max={1}
        helper={copy.imageHelper}
        photos={preview ? [{ id: 'banner', src: preview }] : []}
        disabled={save.loading}
        onAdd={(files) => {
          if (!files[0]) return;
          setFile(files[0]);
          setPreview(URL.createObjectURL(files[0]));
        }}
        onRemove={() => {
          // 사진 칸이 1장뿐이라 지워야 새 이미지를 고를 수 있다 — 기존 배너도 새 이미지를 고르기 전에는 저장 버튼이 꺼진다
          setFile(null);
          setPreview(null);
        }}
      />
      {preview && <img className={styles.bannerImage} src={preview} alt="" />}{/* 고객 앱과 같은 비율로 잘린 모습 미리보기 (장식) */}

      <TextField label={copy.titleLabel} placeholder={copy.titlePlaceholder} hint={copy.titleHint} value={draft.title} maxLength={TITLE_MAX} onChange={(e) => set({ title: e.target.value })} />
      <TextField label={copy.descriptionLabel} placeholder={copy.descriptionPlaceholder} value={draft.description} maxLength={DESCRIPTION_MAX} onChange={(e) => set({ description: e.target.value })} />
      <TextField label={copy.startsOnLabel} type="date" hint={copy.startsOnHint} value={draft.startsOn} onChange={(e) => set({ startsOn: e.target.value })} />
      <TextField label={copy.endsOnLabel} type="date" hint={copy.endsOnHint} value={draft.endsOn} onChange={(e) => set({ endsOn: e.target.value })} />

      {!isNew && (
        <Button variant="destructive" chevron={false} onClick={() => setDeleteOpen(true)}>
          {copy.delete}
        </Button>
      )}

      <Sheet open={deleteOpen} onClose={() => setDeleteOpen(false)} title={copy.deleteTitle}>
        <p className={cx('text-body-s', styles.secondary)}>{copy.deleteDescription}</p>
        <ActionError error={remove.error} />
        <Button variant="destructive" chevron={false} loading={remove.loading} onClick={remove.run}>
          {copy.deleteConfirm}
        </Button>
        <Button variant="secondary" chevron={false} onClick={() => setDeleteOpen(false)}>
          {copy.cancel}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
