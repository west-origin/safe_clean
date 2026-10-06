// Figma: NoticeChip (2031:583) — 주의사항 선택 칩. State=Default|Selected, Icon(INSTANCE_SWAP, 기본 NoticeIcon/Dots)
//
// 아이콘 + 글자로 된 켜고 끄는 칩(토글 버튼). 예: 고객 앱 예약의 주의사항·요청 화면(BookingNotesRequests)에서
// 여러 개를 골라 켜고 끈다. onToggle 없이 배지(보여 주기만 하는 칩)로 쓰는 곳도 있다 (예: SelectionSummary).
// 아이콘은 이름(icon="pet")으로 고르고, 이름 ↔ 아이콘 표는 icons/noticeIcons.js 에 있다.
import { NOTICE_ICONS } from '../../icons/noticeIcons.js';
import { cx } from '../../utils/cx.js';
import styles from './NoticeChip.module.css';

/**
 * @param {object} props
 * @param {string} props.label
 * @param {keyof typeof NOTICE_ICONS} [props.icon='dots'] NoticeIcon 이름 (pet, baby, smoke, lock, stairs, elevator, tools, warning, dots, box, building, parking, droplet, clock, shield, desk, server, ac)
 * @param {boolean} props.selected State=Selected
 * @param {(selected: boolean) => void} [props.onToggle]
 * @param {string} [props.className]
 * 선택 상태는 부모가 들고 있다(제어 컴포넌트). 누르면 onToggle(반대값) 만 부르고 스스로 바뀌지 않는다.
 */
export default function NoticeChip({ label, icon = 'dots', selected = false, onToggle, className }) {
  // 없는 아이콘 이름이면 빈 칸으로 그리지 않고 바로 오류를 낸다 — 오타를 개발 중에 곧바로 알 수 있다.
  const Icon = NOTICE_ICONS[icon];
  if (!Icon) throw new Error(`NoticeChip: 알 수 없는 icon "${icon}"`);
  // aria-pressed 는 "켜짐/꺼짐" 이 있는 버튼이라는 뜻이다. 화면 읽기 도구가 선택 여부를 읽고, CSS 는 .selected 로 칠한다.
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={cx(styles.chip, selected && styles.selected, className)}
      onClick={() => onToggle && onToggle(!selected)}
    >
      <Icon className={styles.icon} />
      <span className={cx('text-caption-medium', styles.label)}>{label}</span>
    </button>
  );
}
