// 라벨 + 값 한 줄. Figma 의 SummaryRow(Job/Complete) · StatRow(Settlement) · BreakdownRow/FinalRow(Settlement/Detail) 공통.
// Card padding="list" divided 안에 넣어 쓴다. 상하 14 (size lg 는 18).
// 쓰는 곳: 매니저 앱의 요약·목록 카드(예: JobComplete, SettlementDetail).
// 라벨은 왼쪽, 값은 오른쪽 끝에 붙고 값은 줄바꿈하지 않는다.
import { cx } from '../../utils/cx.js';
import styles from './SummaryRow.module.css';

/**
 * @param {object} props
 * @param {string} props.label
 * @param {string} props.value
 * @param {'default'|'secondary'|'success'|'brand'|'issue'} [props.tone='default'] 값 색 (issue 는 코드에서 정함: 업로드 실패 등)
 * @param {'body'|'small'|'card'|'heading'} [props.emphasis='small'] 값 글꼴: Body/Default · Title/Small · Title/Card · Heading/S
 * @param {boolean} [props.labelBold] 라벨을 Title/Card 로 (FinalRow)
 * @param {'secondary'|'primary'} [props.labelTone='secondary'] 라벨 색 (Availability DayRow 는 primary)
 * @param {'sm'|'md'|'lg'} [props.size='md'] 상하 여백 12 / 14 / 18
 * @param {string} [props.className]
 */
// 라벨 + 값 한 줄을 그린다. 글꼴은 emphasis·labelBold 로, 색은 tone·labelTone 으로, 상하 여백은 size 로 고른다.
export default function SummaryRow({ label, value, tone = 'default', emphasis = 'small', labelBold = false, labelTone = 'secondary', size = 'md', className }) {
  // emphasis 값 → 값에 붙일 전역 글꼴 클래스(.text-*)
  const valueClass = { body: 'text-body-default', small: 'text-title-small', card: 'text-title-card', heading: 'text-heading-s' }[emphasis];
  return (
    <div className={cx(styles.row, styles[`size-${size}`], className)}>
      {/* 라벨: labelBold 면 글꼴 Title/Card + 진한 색. labelTone="primary" 면 글꼴은 Body/Default 그대로 두고 색만 진하게 */}
      <span className={cx(labelBold ? 'text-title-card' : 'text-body-default', labelBold || labelTone === 'primary' ? styles.labelBold : styles.label)}>{label}</span>
      <span className={cx(valueClass, styles.value, styles[tone])}>{value}</span>
    </div>
  );
}
