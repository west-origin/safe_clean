// Figma: Service/LivePortrait › VideoCard › LiveBadge (2167:2266) · LiveLandscape › NameRow › LiveBadge (2165:2231) — 56×24, 빨강(--raw-danger), 흰 점 6 + "LIVE" Title/XS 흰색, 패딩 4/9/4/8, 간격 5
// 영상 위 빨간 LIVE 배지(공용 UI). 고객 앱 ServiceLivePortrait 는 실시간일 때 영상 왼쪽 위에 직접 넣고,
// ManagerIdentity(onVideo, 가로 라이브 화면)는 매니저 이름 옆에 넣는다.
import { cx } from '../../utils/cx.js';
import styles from './LiveBadge.module.css';

/**
 * @param {object} props
 * @param {string} [props.label='LIVE'] (Figma 문구)
 * @param {string} [props.className]
 * 배지 하나를 그린다. 흰 점은 장식이라 스크린리더에서 숨긴다(aria-hidden).
 */
export default function LiveBadge({ label = 'LIVE', className }) {
  return (
    <span className={cx(styles.badge, className)}>
      <span className={styles.dot} aria-hidden="true" />
      <span className={cx('text-title-xs', styles.label)}>{label}</span>
    </span>
  );
}
