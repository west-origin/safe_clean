// 화면 공통 뼈대. Figma 화면 프레임 구조(StatusBar → [NavBar] → Content → [StickyCTA] → [TabBar]) 그대로.
// 폭은 기기를 따라가고(폰은 꽉 채움), --screen-max-width(480px)보다 넓은 화면에서만 가운데 정렬.
// Content 는 Figma 대로 상 20(NavBar 있으면 8) / 좌우 24 / 하 32 패딩.
// StickyCTA 는 Figma 대로 상단 1px border/default, 패딩 14/24/28, 버튼 간격 10.
// 두 앱의 화면이 이 틀 안에 내용을 넣는다 (ScreenError·ScreenLoading 도 이 틀을 쓴다).
// 하단 영역(StickyCTA + TabBar)은 position: sticky 라서 내용이 길어 스크롤해도 화면 아래에 붙어 있다.
import StatusBar from '../StatusBar/StatusBar.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ScreenLayout.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} [props.navBar] <NavBar/> (없으면 StatusBar 바로 아래 Content)
 * @param {import('react').ReactNode} [props.tabBar] <ManagerTabBar/> 또는 <TabBar/>
 * @param {import('react').ReactNode} [props.stickyCta] 하단 고정 버튼 영역 (Figma StickyCTA)
 * @param {'xl'|'lg'|'ml'|'md'|'sm'|'none'} [props.gap='lg'] Content 자식 세로 간격 xl=28(원본값), lg=24, ml=22(원본값), md=20, sm=16, none=0
 * @param {'default'|'lg'} [props.topPadding='default'] Content 상단 여백 20 / 32 (Job/Complete)
 * @param {boolean} [props.noPadding] Content 패딩 없음 (full-bleed 화면용)
 * @param {boolean} [props.statusBar=true] false 면 StatusBar 여백 없이 내용이 상태바 밑까지 깔린다 (온보딩 슬라이드)
 * @param {import('react').ReactNode} props.children Content
 * @param {string} [props.className]
 */
export default function ScreenLayout({ navBar, tabBar, stickyCta, gap = 'lg', topPadding = 'default', noPadding = false, statusBar = true, children, className }) {
  // Content(<main>) 클래스: gap → .gap-<값>, NavBar 가 있으면 상단 여백 8(.withNav), topPadding='lg' 면 32, noPadding 이면 0.
  // 여백 클래스가 겹치면 CSS 파일에서 뒤에 적힌 규칙이 이긴다 (.topLg 가 .withNav 를, .noPadding 이 둘 다를 덮는다).
  // 하단 영역은 stickyCta 나 tabBar 가 있을 때만 만든다.
  return (
    <div className={cx(styles.screen, className)}>
      {statusBar && <StatusBar />}
      {navBar}
      <main className={cx(styles.content, styles[`gap-${gap}`], navBar && styles.withNav, topPadding === 'lg' && styles.topLg, noPadding && styles.noPadding)}>{children}</main>
      {(stickyCta || tabBar) && (
        <div className={styles.bottom}>
          {stickyCta && <div className={styles.stickyCta}>{stickyCta}</div>}
          {tabBar}
        </div>
      )}
    </div>
  );
}
