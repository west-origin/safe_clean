// Figma: Manager/TabBar (2109:2312) — Active=Home|Call|Schedule|Settlement|MY, Show Call Badge BOOLEAN, Call Badge Count TEXT
// 393×83. 탭 5개(각 78.6), 아이콘 24 + 라벨 Caption. 활성: text/primary + Bold + stroke 2, 비활성: text/secondary + Medium + stroke 1.5
// 매니저 앱 하단 탭. 탭이 있는 화면(예: Home·Profile)이 ScreenLayout·ScreenError·ScreenLoading 의 tabBar 자리에 넣고,
// onSelect 에 useManagerTabs() 가 돌려준 함수를 연결해 탭 key 에 맞는 경로로 이동한다.
import { MANAGER_TABS } from './tabs.js';
import { cx } from '../../utils/cx.js';
import styles from './ManagerTabBar.module.css';

/**
 * @param {object} props
 * @param {'home'|'call'|'schedule'|'settlement'|'my'} props.active 활성 탭 (Figma "Active")
 * @param {(key: string) => void} [props.onSelect] 탭을 눌렀을 때. 화면에서 navigate 로 연결
 * @param {boolean} [props.showCallBadge] (Figma "Show Call Badge")
 * @param {string|number} [props.callBadgeCount] (Figma "Call Badge Count")
 * @param {string} [props.className]
 * 탭 5개를 버튼으로 그린다. 누르면 onSelect(key) 만 부르고, 실제 화면 이동은 부르는 쪽이 한다.
 */
export default function ManagerTabBar({ active, onSelect, showCallBadge = false, callBadgeCount, className }) {
  return (
    <nav className={cx(styles.tabBar, className)} aria-label="주요 메뉴">
      {MANAGER_TABS.map(({ key, label, Icon }) => {
        const isActive = key === active;
        const badge = key === 'call' && showCallBadge;
        // 활성 탭은 aria-current="page" 와 굵은 글자·두꺼운 선으로 드러낸다 (색만으로 구분하지 않는다).
        // 콜 배지는 숫자만 보이므로 배지 span 에 aria-label("새 콜 N건")로 뜻을 적어 둔다 (테스트도 이 라벨로 찾는다).
        return (
          <button
            key={key}
            type="button"
            className={cx(styles.tab, isActive && styles.active)}
            aria-current={isActive ? 'page' : undefined}
            onClick={() => onSelect && onSelect(key)}
          >
            <span className={styles.iconWrap}>
              <Icon strokeWidth={isActive ? 2 : 1.5} />
              {badge && (
                <span className={cx('text-caption-bold', styles.badge)} aria-label={`새 콜 ${callBadgeCount}건`}>
                  {callBadgeCount}
                </span>
              )}
            </span>
            <span className={isActive ? 'text-caption-bold' : 'text-caption-medium'}>{label}</span>
          </button>
        );
      })}
    </nav>
  );
}
