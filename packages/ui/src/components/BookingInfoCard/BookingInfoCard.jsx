// Figma: Booking/Complete › BookingInfoCard (2098:2191) — 344×210 흰 카드, radius 20, 그림자, 패딩 27/25/27/18, 간격 12.
//   InfoRow: InfoIcon 20 + 라벨(Title/XS, 폭 74) + ValueField(surface/field, radius 8, 높이 30, 패딩 12, Caption/L text/secondary), 간격 10
import InfoCalendarIcon from '../../icons/InfoCalendarIcon.jsx';
import InfoClockIcon from '../../icons/InfoClockIcon.jsx';
import InfoPinIcon from '../../icons/InfoPinIcon.jsx';
import InfoPersonIcon from '../../icons/InfoPersonIcon.jsx';
import InfoWonIcon from '../../icons/InfoWonIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './BookingInfoCard.module.css';
// 예약 완료 카드: '아이콘 + 라벨 + 값' 행을 세로로 쌓는다. 고객 앱 BookingComplete 화면이 쓰고,
// 행 목록(아이콘·라벨)은 api 의 getCompleteRows 가, 값은 조회한 예약에서 화면이 채워 넘긴다.

// rows[].icon 이름 → 아이콘 컴포넌트. 여기에 없는 이름을 넘기면 Icon 이 undefined 라 렌더 중 오류가 난다
const ICONS = { calendar: InfoCalendarIcon, clock: InfoClockIcon, pin: InfoPinIcon, person: InfoPersonIcon, won: InfoWonIcon };

/**
 * @param {object} props
 * @param {{ icon: 'calendar'|'clock'|'pin'|'person'|'won', label: string, value: string }[]} props.rows — won 은 코드에서 정한 금액 행(B3)
 * @param {string} [props.className]
 */
export default function BookingInfoCard({ rows, className }) {
  // 정의 목록(dl): dt = 라벨, dd = 값. 라벨·값이 한 쌍이라는 것을 마크업으로 드러낸다.
  // dl 안에서 행마다 div 로 dt·dd 를 묶는 것은 HTML 이 허용하는 구조다.
  // 행의 React key 가 라벨이라, 같은 라벨의 행을 두 번 넘기면 안 된다
  return (
    <dl className={cx(styles.card, className)}>
      {rows.map((r) => {
        const Icon = ICONS[r.icon];
        return (
          <div key={r.label} className={styles.row}>
            <Icon className={styles.icon} />
            <dt className={cx('text-title-xs', styles.label)}>{r.label}</dt>
            <dd className={cx('text-caption-l', styles.value)}>{r.value}</dd>
          </div>
        );
      })}
    </dl>
  );
}
