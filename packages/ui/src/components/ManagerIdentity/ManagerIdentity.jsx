// Figma: Service/LivePortrait › ManagerRow (2167:2254) — 아바타 48 + 이름 Title/Small + 소개 Body/XS text/secondary (간격 12, 글 간격 3) + 우측 슬롯(RealtimePill)
//        Service/LiveLandscape › ManagerIdentity (2165:2226) — 아바타 44 + 이름 Title/Card 흰색 + LiveBadge (간격 8) + 소개 Body/XS 흰 82% (간격 10, 글 간격 4) → variant="onVideo"
// 고객 앱 라이브 화면의 매니저 소개 줄. ServiceLivePortrait 는 light(실시간이면 오른쪽에 RealtimePill),
// ServiceLiveLandscape 는 onVideo(영상 위 흰 글자, 실시간이면 LIVE 배지)로 쓴다.
import LiveBadge from '../LiveBadge/LiveBadge.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ManagerIdentity.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string}} props.avatar ASSETS.managerAvatar
 * @param {string} props.name (예: "김미영 매니저")
 * @param {string} props.description (예: "경력 7년 · 정리정돈부터 꼼꼼한 생활청소까지")
 * @param {'light'|'onVideo'} [props.variant='light'] light=LivePortrait ManagerRow, onVideo=LiveLandscape(흰 글자 + LIVE 배지)
 * @param {boolean} [props.liveBadge] LIVE 배지 표시 여부 (기본: onVideo 일 때). 실시간이 아닐 때(녹화본·중단) 는 false
 * @param {import('react').ReactNode} [props.trailing] 우측 슬롯 (LivePortrait 의 RealtimePill)
 * @param {string} [props.className]
 * 아바타·이름(+LIVE 배지)·소개·우측 슬롯을 한 줄로 그린다. 아바타는 alt=""(장식 이미지)이고 이름은 옆 글자로 읽힌다.
 */
export default function ManagerIdentity({ avatar, name, description, variant = 'light', liveBadge, trailing, className }) {
  const onVideo = variant === 'onVideo';
  const showBadge = liveBadge ?? onVideo; // 녹화본·중단 중에는 화면이 false 를 넘긴다
  return (
    <div className={cx(styles.row, styles[variant], className)}>
      <img className={styles.avatar} src={avatar.src} srcSet={avatar.srcSet} alt="" />
      <div className={styles.text}>
        <div className={styles.nameRow}>
          <span className={cx(onVideo ? 'text-title-card' : 'text-title-small', styles.name)}>{name}</span>
          {showBadge && <LiveBadge />}
        </div>
        <span className={cx('text-body-xs', styles.description)}>{description}</span>
      </div>
      {trailing}
    </div>
  );
}
