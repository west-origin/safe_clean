// Figma: 냉장고 고장 증상 선택 (1:515) — StepProgress 2/8 → 질문("가전수리 · 냉장고") → 고장 증상(OptionGroup plain, 6개) → "다음". 다음 → /detail
// Figma 의 증상 라벨은 폭 81 로 잘려 있다(디자인 결함) — 라벨이 폭을 채우게 둔다 (PROGRESS.md 판단 항목)
// 가전수리 2단계. 앞 단계(RepairAppliance)에서 고른 가전의 증상 목록을 보여 주고, 고른 증상 key 를 초안에 저장한다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, OptionGroup, Button, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getRepairBooking } from '../../../api/customer.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName, symptomsFor } from './repairFlow.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import styles from './repair.module.css';

/** 고장 증상 한 가지를 고르는 화면. 목록은 symptomsFor(가전별, 없으면 공통 목록) */
export default function RepairSymptom() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  const { step, total } = stepOf(category, 'symptom');
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={styles.stack}>
          <StepQuestion service={serviceName(category, catalog, draft)} title={catalog.copy.symptom.title} description={catalog.copy.symptom.description} />
          <OptionGroup bordered={false} ariaLabel="고장 증상" options={symptomsFor(catalog, draft.appliance)} value={draft.symptom} onChange={(symptom) => update({ symptom })} />
        </div>
        <div className={styles.cta}>
          {/* next: 확인 화면에서 고치러 왔으면(?returnTo=) 그리로 돌아가고, 아니면 증상 상세 단계로 */}
          <Button variant="primary" className={styles.ctaButton} disabled={!draft.symptom} onClick={() => navigate(back.next(path(category, 'detail')))}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
