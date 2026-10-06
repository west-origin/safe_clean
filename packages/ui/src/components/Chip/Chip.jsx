// Figma: Chip (2060:1801) — 예약 내역의 선택 결과 표시용 읽기 전용 칩. 텍스트 길이에 따라 폭 자동.
// 쓰는 곳 예: 고객 앱 BookingConfirm(선택 옵션·주의사항), 매니저 앱 ProfileDetail.
// 누를 수 없는 표시용이라 <span> 으로 그린다. 켜고 끄는 칩은 따로 있다(ToggleChip, NoticeChip).
import { cx } from '../../utils/cx.js';
import styles from './Chip.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.children 라벨
 * @param {string} [props.className]
 */
export default function Chip({ children, className }) {
  return <span className={cx('text-caption-medium', styles.chip, className)}>{children}</span>;
}
