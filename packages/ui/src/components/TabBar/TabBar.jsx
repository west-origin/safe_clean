// Figma: TabBar (2045:1714) — 고객용 하단 탭. Active=Home|Repair|Reservations|Settings
// 393×83, 탭 4개 균등, 아이콘 30 + 라벨 Body/Default. 비활성: 아이콘 35% + text/secondary.
// 쓰는 곳: 고객 앱 Home·Repair·Reservations·Settings 화면이 ScreenLayout 의 tabBar 로 넘긴다.
// 화면 이동은 하지 않고 onSelect(key) 만 부른다. 고객 앱은 useCustomerTabs() 가 key 를 경로로 바꿔 이동한다.
// 매니저 앱 하단 탭은 별도 컴포넌트(ManagerTabBar)다.
import { CUSTOMER_TABS } from './tabs.js';
import { cx } from '../../utils/cx.js';
import styles from './TabBar.module.css';

/**
 * @param {object} props
 * @param {'home'|'repair'|'reservations'|'settings'} props.active (Figma "Active")
 * @param {(key: string) => void} [props.onSelect]
 * @param {string} [props.className]
 */
// 탭 목록(CUSTOMER_TABS)을 버튼으로 그리고, active 와 key 가 같은 탭에 aria-current="page" 와 active 클래스를 붙인다.
export default function TabBar({ active, onSelect, className }) {
  return (
    <nav className={cx(styles.tabBar, className)} aria-label="주요 메뉴">
      {/* 탭 정의(tabs.js)를 차례로 그린다. 아이콘은 SVG 컴포넌트(Icon) 또는 래스터 그림(image) 중 하나다 */}
      {CUSTOMER_TABS.map(({ key, label, Icon, image }) => {
        const isActive = key === active;
        return (
          <button
            key={key}
            type="button"
            className={cx(styles.tab, isActive && styles.active)}
            aria-current={isActive ? 'page' : undefined}
            onClick={() => onSelect && onSelect(key)}
          >
            <span className={styles.icon}>
              {Icon ? <Icon /> : <img src={image.src} srcSet={image.srcSet} width="30" height="30" alt="" />}
            </span>
            <span className="text-body-default">{label}</span>
          </button>
        );
      })}
    </nav>
  );
}
