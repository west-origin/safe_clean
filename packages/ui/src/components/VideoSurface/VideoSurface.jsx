// Figma: Service/LivePortrait › VideoCard (2167:2262) 345×430 radius 20 · Service/LiveLandscape (2165:2222) 852×480
// VideoSurface (placeholder · 코드에서 <video>로 구현) → <video playsInline muted autoPlay>. 포스터 이미지는 넣지 않는다 (CLAUDE.md).
// Scrim/Top·Bottom 은 variant 별 그라디언트. 오버레이(배지·컨트롤)는 children 으로 받아 위에 얹는다. 실제 스트림 연결은 단계 5(useLiveSession).
// 쓰는 곳 예: 고객 앱 라이브 화면(ServiceLivePortrait·ServiceLiveLandscape), 매니저 앱 작업 진행 화면(JobInProgress)의 바디캠 프리뷰.
import { forwardRef } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './VideoSurface.module.css';

/**
 * @param {object} props
 * @param {'portrait'|'landscape'} [props.variant='portrait'] portrait=VideoCard(radius 20, 스크림 96/130), landscape=전체 화면(스크림 132/160)
 * @param {boolean} [props.muted=true] <video muted>
 * @param {import('react').ReactNode} [props.children] 영상 위 오버레이
 * @param {string} [props.className]
 * @param {import('react').Ref<HTMLVideoElement>} ref useLiveSession 이 트랙을 붙이는 <video>
 * forwardRef: 부모가 준 ref 를 바깥 div 가 아니라 안쪽 <video> 에 붙인다. 화면은 useLiveSession 이 돌려준 videoRef
 * (liveClient.attachVideo, 마운트 때 요소·언마운트 때 null 을 받는 함수)를 ref 로 넘긴다. 스크림 두 겹은 장식이라 aria-hidden.
 */
const VideoSurface = forwardRef(function VideoSurface({ variant = 'portrait', muted = true, children, className }, ref) {
  return (
    <div className={cx(styles.surface, styles[variant], className)}>
      <video ref={ref} className={styles.video} playsInline muted={muted} autoPlay />
      <div className={cx(styles.scrim, styles.scrimTop)} aria-hidden="true" />
      <div className={cx(styles.scrim, styles.scrimBottom)} aria-hidden="true" />
      {children}
    </div>
  );
});

export default VideoSurface;
