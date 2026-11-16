// Manager/Admin/NoticeEdit (코드에서 정함, rbac.md §15-23) — Figma 에 없다. 공지 관리 목록에서 '공지 올리기'(/admin/notices/new)
// 또는 공지 한 줄(/admin/notices/:id) → 여기. 보여줄 앱(고객·매니저·전체) · 제목 · 내용을 쓰고 올리거나 저장한다. 기존 공지는 삭제도 된다(확인 시트).
// 성공하면 목록으로 돌아가며 토스트. 서버 검사 문구(VALIDATION)는 버튼 위에 그대로 보인다.
// 기존 ui 프리미티브(ScreenLayout·NavBar·SegmentedControl·TextField·TextArea·Button·Sheet)만 조립한다. 문구는 api/mock.js ADMIN_NOTICE_COPY.
import { useId, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, SegmentedControl, TextField, TextArea, Button, Sheet, ActionError, ScreenError, ScreenLoading, useAsync, useAction, showToast, cx } from '@ansimclean/ui';
import { getAdminNoticeCopy, getAdminNotice, createAdminNotice, updateAdminNotice, deleteAdminNotice, newNoticeKey } from '../../api/manager.js';
import styles from './admin.module.css';

const TITLE_MAX = 100; // 서버 Notice.TITLE_MAX 와 같다
const BODY_MAX = 5000; // 서버 Notice.BODY_MAX 와 같다

/** 공지 쓰기·고치기 (/admin/notices/new · /admin/notices/:id) */
export default function AdminNoticeEdit() {
  const navigate = useNavigate();
  const { id } = useParams();
  const isNew = id === 'new';
  const bodyId = useId();
  const { data, error, reload } = useAsync(() => Promise.all([getAdminNoticeCopy(), isNew ? null : getAdminNotice(id)]), [id]);
  const [draft, setDraft] = useState(null); // { audience, title, body } — 불러온 뒤 채운다
  const [submitKey] = useState(() => newNoticeKey()); // 새 공지 한 번에 키 하나 (재시도해도 두 번 올리지 않게)
  const [deleteOpen, setDeleteOpen] = useState(false);
  // 불러온 값으로 입력칸을 한 번 채운다 (렌더 중 한 번만 — effect 로 하면 한 번 더 그린다)
  if (data && !draft) {
    const notice = data[1];
    setDraft(notice ? { audience: notice.audience, title: notice.title, body: notice.body } : { audience: 'customer', title: '', body: '' });
  }

  const save = useAction(async () => {
    if (isNew) await createAdminNotice({ ...draft, submitKey });
    else await updateAdminNotice(id, draft);
    showToast(isNew ? data[0].created : data[0].saved);
    navigate('/admin/notices', { replace: true });
  });
  const remove = useAction(async () => {
    await deleteAdminNotice(id);
    setDeleteOpen(false);
    showToast(data[0].deleted);
    navigate('/admin/notices', { replace: true });
  });

  const back = () => navigate('/admin/notices');
  const title = data ? (isNew ? data[0].newTitle : data[0].editTitle) : '';
  const navBar = <NavBar title={title} onBack={back} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data || !draft) return <ScreenLoading navBar={navBar} />;
  const [copy] = data;
  const set = (patch) => setDraft((d) => ({ ...d, ...patch }));
  const ready = draft.title.trim() && draft.body.trim();

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
      <section className={styles.field}>
        <p className={cx('text-title-small', styles.label)}>{copy.audienceLabel}</p>
        <SegmentedControl ariaLabel={copy.audienceLabel} options={copy.audiences} value={draft.audience} onChange={(audience) => set({ audience })} />
      </section>

      <TextField label={copy.titleLabel} placeholder={copy.titlePlaceholder} value={draft.title} maxLength={TITLE_MAX} onChange={(e) => set({ title: e.target.value })} />

      <section className={styles.field}>
        <label htmlFor={bodyId} className={cx('text-title-small', styles.label)}>{copy.bodyLabel}</label>
        <TextArea id={bodyId} placeholder={copy.bodyPlaceholder} value={draft.body} maxLength={BODY_MAX} rows={10} onChange={(e) => set({ body: e.target.value })} />
      </section>

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
