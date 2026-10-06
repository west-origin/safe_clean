// Figma: 가전수리 종류 선택 (1:493) — StepProgress 1/8 → 질문 → 가전 종류(OptionGroup, 6개) → "다음"(304). 다음 → /symptom
// 수리 예약의 첫 단계 (가전수리만). 수리 탭(Repair.jsx)에서 '가전수리' 를 누르면 여기로 온다.
// 고른 가전은 수리 예약 초안(sessionStorage)에 바로 저장된다. 확인 화면의 '수정' 으로 왔으면 ?returnTo= 가 붙어 있다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, OptionGroup, Button, ScreenError, ScreenLoading, useAsync } from '@ansimclean/ui';
import { getRepairBooking } from '../../../api/customer.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, categoryLabel } from './repairFlow.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import styles from './repair.module.css';

/**
 * 가전 종류 한 가지를 고르는 화면. getRepairBooking 결과(앱 안의 문구·가전 목록 + 서버 시간표·현장 상황 칩)를 받은 뒤 그린다.
 * 이 화면은 앱 안 값만 쓰지만 getRepairBooking 이 서버도 부르므로, 서버가 안 되면 이 화면도 오류 화면('다시 시도')이 된다
 */
export default function RepairAppliance() {
  const navigate = useNavigate();
  const { category } = useParams(); // 주소의 :category — 이 화면은 'appliance' 로만 온다
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  const { step, total } = stepOf(category, 'appliance');
  // 첫 단계라 뒤로 가기는 브라우저 기록 대신 수리 탭으로 보낸다
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate('/repair')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={styles.stack}>
          <StepQuestion service={categoryLabel(category)} title={catalog.copy.appliance.title} description={catalog.copy.appliance.description} />
          {/* 가전을 누를 때마다(같은 가전을 다시 눌러도) 고른 증상을 지운다 — 증상 목록이 가전마다 달라 이전 증상이 새 목록에 없을 수 있다 */}
          <OptionGroup ariaLabel="가전 종류" options={catalog.appliances} value={draft.appliance} onChange={(appliance) => update({ appliance, symptom: undefined })} />
        </div>
        <div className={styles.cta}>
          {/* carry: 증상 목록이 가전에 따라 달라지므로 확인 화면에서 고치러 왔어도 증상 단계를 거치게 하고, returnTo 를 넘겨 그다음엔 확인 화면으로 돌아가게 한다 */}
          <Button variant="primary" className={styles.ctaButton} disabled={!draft.appliance} onClick={() => navigate(back.carry(path(category, 'symptom')))}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
