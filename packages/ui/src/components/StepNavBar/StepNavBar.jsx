// Figma: Booking/* › NavBar (2082:1835) — Button/Back 24 + StepProgress(Step=1~8) + Spacer 24. 높이 44, 패딩 10/18.
//        Booking/AddressSearch·AddressConfirm › SheetHeader (2082:1887) — 같은 구성, 높이 30, 패딩 없음(시트 안쪽 24 안에), Spacer 30 → variant="sheet"
// 예약 단계 화면 맨 위 줄: 왼쪽 뒤로가기, 가운데 단계 점(StepProgress), 오른쪽 빈 칸.
// 쓰는 곳: 고객 앱 청소 예약·수리 예약 단계 화면. 보통 ScreenLayout(·ScreenLoading·ScreenError) 의 navBar 로 넘기고
// (예: BookingConfirm), 흰 시트 영역 안에 두는 화면은 variant="sheet" 로 직접 넣는다(예: BookingAddressSearch).
import BackArrowIcon from '../../icons/BackArrowIcon.jsx';
import StepProgress from '../StepProgress/StepProgress.jsx';
import { cx } from '../../utils/cx.js';
import styles from './StepNavBar.module.css';

/**
 * @param {object} props
 * @param {number} props.step 현재 단계 1~8 (Figma StepProgress "Step")
 * @param {number} [props.total=8] 전체 단계 (수리 예약 가전수리 외 카테고리는 6)
 * @param {() => void} [props.onBack]
 * @param {'bar'|'sheet'} [props.variant='bar'] bar=NavBar(44, 패딩 10/18), sheet=SheetHeader(30, 패딩 없음)
 * @param {string} [props.className]
 */
// 뒤로가기 버튼 + 단계 점 + 빈 칸을 한 줄로 그린다. 화면 이동은 하지 않고 onBack 만 부른다(이동은 화면이 정한다).
export default function StepNavBar({ step, total = 8, onBack, variant = 'bar', className }) {
  return (
    <div className={cx(styles.bar, styles[variant], className)}>
      <button type="button" className={styles.back} onClick={onBack} aria-label="뒤로가기">
        <BackArrowIcon />
      </button>
      <StepProgress step={step} total={total} />
      {/* 오른쪽 빈 칸: 뒤로가기(24)와 폭을 맞춰 단계 점을 가운데에 둔다(sheet 는 Figma 대로 30). 스크린리더는 건너뛴다 */}
      <span className={cx(styles.spacer, variant === 'sheet' && styles.spacerSheet)} aria-hidden="true" />
    </div>
  );
}
