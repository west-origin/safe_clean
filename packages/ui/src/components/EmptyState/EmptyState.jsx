// 빈 상태. Figma: Manager/Home/Empty › EmptyState, Manager/Call/Empty › EmptyState (Schedule/Empty 도 같은 구조)
// 84px 회색 원 안에 36px 아이콘, 제목 Heading/S, 설명 Body/Default, 세로 간격 14, 상하 72.
// 목록이 비었을 때 목록 자리에 대신 보여 준다. 두 앱의 화면이 데이터가 비었을 때 조건부로 그린다
// (별도 라우트가 아니다). 예: 매니저 앱 CallList, 고객 앱 Reservations.
import EmptySmileIcon from '../../icons/EmptySmileIcon.jsx';
import EmptyCallIcon from '../../icons/EmptyCallIcon.jsx';
import EmptyCalendarIcon from '../../icons/EmptyCalendarIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './EmptyState.module.css';

// icon prop 이름 → 원 안에 넣을 아이콘 컴포넌트.
const ICONS = { smile: EmptySmileIcon, call: EmptyCallIcon, calendar: EmptyCalendarIcon };

/**
 * @param {object} props
 * @param {'smile'|'call'|'calendar'} [props.icon='smile']
 * @param {string} props.title
 * @param {string} [props.description]
 * @param {string} [props.className]
 */
export default function EmptyState({ icon = 'smile', title, description, className }) {
  // 모르는 icon 이름이 오면 깨지지 않게 웃는 얼굴로 대신한다.
  const Icon = ICONS[icon] || EmptySmileIcon;
  // role="status": 스크린리더가 이 안내를 끼어들지 않고 읽어 준다.
  return (
    <div className={cx(styles.empty, className)} role="status">
      <span className={styles.circle}>
        <Icon />
      </span>
      <p className={cx('text-heading-s', styles.title)}>{title}</p>
      {description && <p className={cx('text-body-default', styles.description)}>{description}</p>}
    </div>
  );
}
