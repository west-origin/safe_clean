// Figma: Manager/Settlement › SettlementCard (2141:2728) — 날짜 + 상태 배지 / 업무명 + 금액. 패딩 16/18, 간격 6.
// 정산 내역 한 건을 보여 주는 카드. 윗줄: 날짜 ↔ 상태 배지, 아랫줄: 업무명 ↔ 금액.
// 예: 매니저 앱 Settlement 화면 목록. 누르면 화면이 /settlement/:id(정산 상세)로 보낸다.
import StatusBadge from '../StatusBadge/StatusBadge.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SettlementCard.module.css';

/**
 * 정산 카드. 날짜·금액은 표시용 문자열을 그대로 받아 그린다(여기서 포맷하지 않는다).
 * @param {object} props
 * @param {string} props.date 예: "9월 14일"
 * @param {string} props.status StatusBadge 의 status (Figma: complete)
 * @param {string} props.title 예: "강남구 기본청소"
 * @param {string} props.amount 예: "120,000원"
 * @param {() => void} [props.onClick] 있으면 카드 전체가 버튼 (Figma: SettlementCard → Settlement/Detail)
 * @param {string} [props.className]
 */
export default function SettlementCard({ date, status, title, amount, onClick, className }) {
  // onClick 이 있을 때만 <button> 으로 그려 키보드·화면 낭독기에서도 누를 수 있게 한다. 없으면 그냥 <div>.
  // type="button" 은 button 일 때만 붙인다 (div 에는 없는 속성).
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag type={onClick ? 'button' : undefined} className={cx(styles.card, className)} onClick={onClick}>
      <div className={styles.row}>
        <span className={cx('text-body-s', styles.date)}>{date}</span>
        <StatusBadge status={status} />
      </div>
      <div className={styles.row}>
        <span className="text-body-default">{title}</span>
        <span className={cx('text-title-card', styles.amount)}>{amount}</span>
      </div>
    </Tag>
  );
}
