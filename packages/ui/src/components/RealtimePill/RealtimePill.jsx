// Figma: Service/LivePortrait › ManagerRow › RealtimePill (2167:2259) — 68×28, 연한 초록 배경(--raw-realtime-pill-bg), 초록 점 6 + "실시간" Title/XS 초록(--raw-realtime-green), 패딩 6/11/6/10, 간격 5
// 라이브 화면의 매니저 줄 오른쪽에 붙는 "실시간" 알약. 고객 앱 ServiceLivePortrait 가 방송 중(isLive)일 때만
// ManagerIdentity 의 trailing 자리에 넣는다. 색은 Figma 에 변수가 없어 extra.css 의 --raw-* 원본색을 쓴다.
import { cx } from '../../utils/cx.js';
import styles from './RealtimePill.module.css';

/**
 * @param {object} props
 * @param {string} [props.label='실시간']
 * @param {string} [props.className]
 * 초록 점은 장식이라 보조기기에서 숨기고(aria-hidden) 글자만 읽힌다.
 */
export default function RealtimePill({ label = '실시간', className }) {
  return (
    <span className={cx(styles.pill, className)}>
      <span className={styles.dot} aria-hidden="true" />
      <span className={cx('text-title-xs', styles.label)}>{label}</span>
    </span>
  );
}
