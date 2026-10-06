// Figma: Manager/StatusBadge (2110:2235) — Status variant 7종. 라벨 문구는 Figma 그대로.
// 작업·일정·정산 상태를 아이콘 + 글자의 알약 모양으로 보여 준다. 상태별 문구·아이콘 표는 status.js, 색은 CSS 의 같은 이름 클래스.
// 예: 공용 UI JobCard·SettlementCard 안, 고객 앱 RepairComplete(label 로 문구를 바꿔 쓴다).
import { STATUS } from './status.js';
import { cx } from '../../utils/cx.js';
import styles from './StatusBadge.module.css';

/**
 * 상태 배지. 모르는 status 면 오류를 던진다 — 오타가 빈 배지로 조용히 지나가지 않는다.
 * @param {object} props
 * @param {'new'|'scheduled'|'inProgress'|'live'|'paused'|'complete'|'settlementPending'} props.status
 * @param {string} [props.label] 문구 오버라이드 (Figma 인스턴스 오버라이드. 예: Profile/Equipment 의 "정상 연결"). 기본은 Figma variant 문구
 * @param {string} [props.className]
 */
export default function StatusBadge({ status, label, className }) {
  const item = STATUS[status];
  if (!item) throw new Error(`StatusBadge: 알 수 없는 status "${status}"`);
  const { Icon } = item;
  // styles[status]: status 이름과 같은 CSS 클래스(.new, .live …)가 배경·글자색을 정한다. 아이콘은 글자색을 따라간다.
  // label ?? item.label: label 을 안 넘기면(undefined/null) Figma 기본 문구를 쓴다.
  return (
    <span className={cx('text-title-xs', styles.badge, styles[status], className)}>
      <Icon />
      {label ?? item.label}
    </span>
  );
}
