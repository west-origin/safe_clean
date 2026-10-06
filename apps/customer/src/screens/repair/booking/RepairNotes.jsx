// Figma: 현장 상황과 요청사항 (1:326) — StepProgress 6/8 → 질문 → 현장 상황 · 중복 선택(ToggleChip 4개) → 기사님께 전할 요청사항(Field/Text) → 안전 안내("!") → "다음". 다음 → /confirm
// 고른 현장 상황 key 배열은 초안 situations 에, 요청사항 글은 request 에 저장한다. 둘 다 선택 사항이다.
// 칩 목록은 서버 카탈로그의 'repair' 주의사항 칩이다 (getRepairBooking). 예약을 만들 때 situations 는 notices 로 보낸다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, ToggleChip, TextField, InfoNote, Button, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getRepairBooking } from '../../../api/customer.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName } from './repairFlow.js';
import { INPUT_LIMITS } from '../../../api/mock.js';
import styles from './repair.module.css';

/** 현장 상황(여러 개 선택)과 기사님께 전할 요청사항을 받는 화면 */
export default function RepairNotes() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [draft, update] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  const { step, total } = stepOf(category, 'notes');
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  const copy = catalog.copy.notes;
  const selected = draft.situations ?? [];
  // 칩 하나를 누르면 이미 골랐으면 빼고, 아니면 끝에 더한다 (중복 선택)
  const toggle = (key) => update({ situations: selected.includes(key) ? selected.filter((k) => k !== key) : [...selected, key] });

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={cx(styles.stack, styles.stackWide)}>
          <StepQuestion service={serviceName(category, catalog, draft)} title={copy.title} description={copy.description} />
          <section className={styles.block} aria-label={copy.situationsTitle}>
            <h2 className={cx('text-title-small', styles.blockTitle)}>{copy.situationsTitle}</h2>
            <div className={styles.chips}>
              {catalog.situations.map((s) => (
                <ToggleChip key={s.key} label={s.label} selected={selected.includes(s.key)} onToggle={() => toggle(s.key)} />
              ))}
            </div>
          </section>
          <TextField label={copy.fieldLabel} placeholder={copy.fieldPlaceholder} value={draft.request ?? ''} onChange={(e) => update({ request: e.target.value })} maxLength={INPUT_LIMITS.request} />
          <InfoNote tone="warning" glyph="!">{copy.safety}</InfoNote>
        </div>
        <div className={styles.cta}>
          {/* 다음 단계가 확인 화면이라 returnTo 를 보지 않고 늘 확인 화면으로 간다 */}
          <Button variant="primary" className={styles.ctaButton} onClick={() => navigate(path(category, 'confirm'))}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
