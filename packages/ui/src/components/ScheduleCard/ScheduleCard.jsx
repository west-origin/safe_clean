// Figma: Manager/ScheduleCard (2112:2252) — 345×108. 시간 + StatusBadge / 고객·지역 / 서비스명
// 매니저 앱의 일정 카드 한 장. 화면이 목록으로 그리고 onClick 에서 이동을 정한다
// (예: 매니저 홈·일정 화면은 작업 상세 /job/:id 로 보낸다).
import StatusBadge from '../StatusBadge/StatusBadge.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ScheduleCard.module.css';

/**
 * @param {object} props
 * @param {string} props.time 예: "10:00 - 13:00"
 * @param {import('../StatusBadge/StatusBadge.jsx').default extends never ? never : string} props.status StatusBadge 의 status
 * @param {string} props.customer 예: "김○○ 고객 · 강남구 역삼동"
 * @param {string} props.service 예: "아파트 기본청소"
 * @param {() => void} [props.onClick] 있으면 카드 전체가 버튼이 된다
 * @param {string} [props.className]
 * status 는 StatusBadge 가 아는 값(예: 'scheduled', 'inProgress')이어야 한다. 모르는 값이면 StatusBadge 가 오류를 던진다.
 */
export default function ScheduleCard({ time, status, customer, service, onClick, className }) {
  // 누를 수 있을 때만 <button> 으로 그려 키보드·화면 읽기 도구에서도 버튼으로 동작하게 한다. 아니면 그냥 <div>.
  // type="button" 은 button 일 때만 붙인다 (form 안에서 제출 버튼으로 동작하지 않게).
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag type={onClick ? 'button' : undefined} className={cx(styles.card, className)} onClick={onClick}>
      <div className={styles.topRow}>
        <span className={cx('text-title-small', styles.time)}>{time}</span>
        <StatusBadge status={status} />
      </div>
      <p className={cx('text-body-s', styles.customer)}>{customer}</p>
      <p className={cx('text-body-s', styles.service)}>{service}</p>
    </Tag>
  );
}
