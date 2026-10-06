// 화면 전환 중(다음 화면의 lazy 청크를 받는 동안) 상단에 가늘게 흐르는 진행 막대 (코드에서 정함 — 검증 검토: 느린 망에서 탭을 눌러도 수 초간 아무 반응이 없어 재탭·이중 이동을 유발했다)
// 고객·매니저 앱의 RouteShell 이 라우터의 navigation.state 가 'loading' 일 때 active 로 켠다.
// 두 앱 라우트에는 loader 가 없어 화면 안의 데이터 로딩에는 켜지지 않는다(그것은 ScreenLoading 이 맡는다).
import { cx } from '../../utils/cx.js';
import styles from './LoadingBar.module.css';

/**
 * @param {object} props
 * @param {boolean} props.active 보일지
 * @param {string} [props.label='화면을 불러오는 중'] 스크린리더용
 * active 가 아니면 아무것도 그리지 않는다(null). 켜져 있으면 role=progressbar + aria-busy 로 불러오는 중임을 알린다.
 */
export default function LoadingBar({ active, label = '화면을 불러오는 중' }) {
  if (!active) return null;
  return (
    <div className={styles.bar} role="progressbar" aria-label={label} aria-busy="true">
      <div className={cx(styles.fill)} />
    </div>
  );
}
