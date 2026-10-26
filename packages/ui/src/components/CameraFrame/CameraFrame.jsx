// (코드에서 정함 — Figma 에 없음, 2026-10-23 사용자 요청) 매니저 앱 사진 촬영 틀.
// 카메라 미리보기 <video> 위에 청소 전 사진(ghostSrc)을 반투명하게 겹쳐, 청소 후 사진을 같은 각도로 맞춰 찍게 한다.
// 찍은 뒤 확인할 때는 previewSrc 로 방금 찍은 사진을 영상 대신 보인다(다시 찍기 / 저장).
// 쓰는 곳 예: 매니저 앱 사진 촬영 화면(screens/job/JobPhotoCapture). 영상은 useLiveSession 의 videoRef 로만 붙는다 (CLAUDE.md §10).
// 비율 3:4 는 고객 앱 BeforeAfterSlider 와 같고, liveClient.capturePhoto 가 같은 비율로 가운데를 잘라 저장한다 — 바꾸면 셋 다 바꾼다.
import { forwardRef } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './CameraFrame.module.css';

/**
 * @param {object} props
 * @param {string} [props.ghostSrc] 반투명하게 겹칠 사진 (청소 후 촬영 때 청소 전 사진). 없으면 영상만
 * @param {string} [props.ghostAlt=''] 겹친 사진 설명 (장식이면 빈 문자열)
 * @param {boolean} [props.ghostOnly=false] 영상 대신 청소 전 원본만 표시한다(카메라 연결은 유지).
 * @param {() => void} [props.onGhostLoad] 청소 전 사진 로딩 완료
 * @param {() => void} [props.onGhostError] 청소 전 사진 로딩 실패
 * @param {string} [props.previewSrc] 방금 찍은 사진. 있으면 영상·겹친 사진 대신 이것을 보인다
 * @param {string} [props.previewAlt='방금 찍은 사진']
 * @param {import('react').ReactNode} [props.children] 틀 위에 얹을 안내 (예: 카메라 연결 문구)
 * @param {string} [props.className]
 * @param {import('react').Ref<HTMLVideoElement>} ref 카메라 영상을 붙일 <video> (useLiveSession 의 videoRef)
 * forwardRef: 부모가 준 ref 를 안쪽 <video> 에 붙인다. 미리보기를 보일 때도 <video> 는 남겨 두어(숨김) 카메라 연결이 끊기지 않는다.
 */
const CameraFrame = forwardRef(function CameraFrame({ ghostSrc, ghostAlt = '', ghostOnly = false, onGhostLoad, onGhostError, previewSrc, previewAlt = '방금 찍은 사진', children, className }, ref) {
  return (
    <div className={cx(styles.frame, className)}>
      <video ref={ref} className={cx(styles.layer, styles.video, (previewSrc || ghostOnly) && styles.hidden)} playsInline muted autoPlay />
      {ghostSrc && !previewSrc && <img src={ghostSrc} alt={ghostAlt} onLoad={onGhostLoad} onError={onGhostError} className={cx(styles.layer, styles.ghost, ghostOnly && styles.referenceOnly)} />}
      {previewSrc && <img src={previewSrc} alt={previewAlt} className={cx(styles.layer, styles.preview)} />}
      {children}
    </div>
  );
});

export default CameraFrame;
