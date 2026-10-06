// Figma: Service/LivePortrait › VideoCard › CleaningStatusCard (2167:2274) — 265×54, 검정 50%, radius 20, 패딩 10/12, 간격 10. IconCircle 34(흰 22%) + 집 아이콘 18, 제목 Title/XS 흰색, 부제 Caption/L 흰 85%
// 고객 앱 라이브 세로 화면(ServiceLivePortrait)의 영상 위에 떠 있는 "지금 무엇을 청소 중인지" 카드.
// 제목·부제는 화면이 받은 라이브 세션 데이터(session.status)를 그대로 넘겨받는다.
import { HomeOutlineIcon } from '../../icons/liveIcons.jsx';
import { cx } from '../../utils/cx.js';
import styles from './CleaningStatusCard.module.css';

/**
 * @param {object} props
 * @param {string} props.title (예: "거실 청소 중")
 * @param {string} props.description (예: "깨끗한 공간을 만들어가고 있어요!")
 * @param {string} [props.className]
 */
// 집 아이콘 원 + 제목·부제 두 줄. role="status" 라서 글이 바뀌면 스크린리더가 끼어들지 않고 읽어 준다.
export default function CleaningStatusCard({ title, description, className }) {
  return (
    <div className={cx(styles.card, className)} role="status">
      <span className={styles.iconCircle} aria-hidden="true">
        <HomeOutlineIcon />
      </span>
      <span className={styles.text}>
        <span className={cx('text-title-xs', styles.title)}>{title}</span>
        <span className={cx('text-caption-l', styles.description)}>{description}</span>
      </span>
    </div>
  );
}
