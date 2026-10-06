// Figma(수리 예약): 예약 정보 카드(1:247) — 흰 카드 radius md, 패딩 20, 그림자(0 8 24 검정 8% 원본값 → extra.css --raw-repair-card-shadow), 항목 간격 14.
//   항목: InfoIcon(20, brand) → 라벨(Title/XS) → 값 상자(surface/field, 높이 34, radius 8, 좌우 12, Caption/L secondary) 또는 children(배지 + 상태 안내), 세로 간격 10
// 예: 고객 앱 RepairComplete(수리 예약 완료) 화면 — 방문 일정·시간·주소는 값 상자, 기사 배정은 children(배지 + 안내).
import InfoCalendarIcon from '../../icons/InfoCalendarIcon.jsx';
import InfoClockIcon from '../../icons/InfoClockIcon.jsx';
import InfoPinIcon from '../../icons/InfoPinIcon.jsx';
import InfoPersonIcon from '../../icons/InfoPersonIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './StackedInfoCard.module.css';

/** rows[].icon 이름 → 아이콘 컴포넌트. 여기에 없는 이름을 주면 Icon 이 undefined 라 렌더 중 오류가 난다 */
const ICONS = { calendar: InfoCalendarIcon, clock: InfoClockIcon, pin: InfoPinIcon, person: InfoPersonIcon };

/**
 * 아이콘·라벨·값을 세로로 쌓은 항목들을 한 카드에 담는다. 보여 주기만 한다.
 * @param {object} props
 * @param {{ icon: 'calendar'|'clock'|'pin'|'person', label: string, value?: string, children?: import('react').ReactNode }[]} props.rows value=값 상자, children=자유 내용(배지 등)
 * @param {string} [props.className]
 */
export default function StackedInfoCard({ rows, className }) {
  return (
    <div className={cx(styles.card, className)}>
      {rows.map((r) => {
        const Icon = ICONS[r.icon];
        // value 가 undefined 가 아니면(빈 문자열도) 회색 값 상자, undefined 면 children 을 그대로 그린다. key 는 label 이라 행마다 달라야 한다.
        return (
          <div key={r.label} className={styles.row}>
            <Icon className={styles.icon} />
            <span className={cx('text-title-xs', styles.label)}>{r.label}</span>
            {r.value !== undefined ? <p className={cx('text-caption-l', styles.value)}>{r.value}</p> : r.children}
          </div>
        );
      })}
    </div>
  );
}
