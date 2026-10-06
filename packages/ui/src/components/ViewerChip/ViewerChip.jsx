// Figma: Service/LivePortrait › VideoCard › ViewerChip (2167:2269) — 검정 45%, 사람 아이콘 13 + 시청자 수 Title/XS 흰색, 패딩 4/9/4/8, 간격 4
// 고객 앱 세로 라이브 화면(ServiceLivePortrait)이 실시간 방송 중(mode === 'live')일 때 영상 위에 시청자 수를 보여 준다.
import { ViewerIcon } from '../../icons/liveIcons.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ViewerChip.module.css';

/**
 * @param {object} props
 * @param {number} props.count 시청자 수
 * @param {string} [props.className]
 * 칩 전체를 role="img" + aria-label("시청자 N명") 한 덩어리로 읽히게 하고, 안의 숫자는 aria-hidden 으로 두 번 읽히지 않게 한다.
 * (span 은 역할이 없으면 aria-label 이 읽히지 않아 role 을 준다 — 테스트 이름 참고)
 */
export default function ViewerChip({ count, className }) {
  return (
    <span className={cx(styles.chip, className)} role="img" aria-label={`시청자 ${count}명`}>
      <ViewerIcon className={styles.icon} />
      <span className={cx('text-title-xs', styles.count)} aria-hidden="true">
        {count}
      </span>
    </span>
  );
}
