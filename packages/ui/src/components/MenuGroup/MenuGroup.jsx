// Figma: Settings › MenuGroup/계정 (2048:1729) … — GroupTitle(Title/Small text/secondary, 좌 2) + Rows(1px border/default, radius 10), 간격 8
// 설정 메뉴 묶음(공용 UI). 두 앱의 설정류 화면이 MenuRow 들을 감싸는 데 쓴다 (예: 고객 앱 Settings, 매니저 앱 Notifications).
import { cx } from '../../utils/cx.js';
import styles from './MenuGroup.module.css';

/**
 * @param {object} props
 * @param {string} props.title 그룹 제목 (Figma 그대로: 계정 / 알림 / 고객지원 / 약관 및 정보)
 * @param {import('react').ReactNode} props.children <MenuRow/> 들
 * @param {string} [props.className]
 * 제목(h2)과 행 묶음을 section 으로 그린다. aria-label 에 제목을 넣어 스크린리더에서 이름 있는 영역(region)으로 잡힌다.
 */
export default function MenuGroup({ title, children, className }) {
  return (
    <section className={cx(styles.group, className)} aria-label={title}>
      <h2 className={cx('text-title-small', styles.title)}>{title}</h2>
      <div className={styles.rows}>{children}</div>
    </section>
  );
}
