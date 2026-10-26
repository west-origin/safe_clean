// (코드에서 정함 — Figma 에 없음, 2026-10-23 사용자 요청) 청소 전·후 비교 사진 한 장.
// 같은 위치에서 찍은 두 사진을 겹쳐 두고, 가운데 세로 막대를 좌우로 밀면 막대 왼쪽은 '청소 전', 오른쪽은 '청소 후' 가 보인다.
// 쓰는 곳 예: 고객 앱 녹화본 화면(ServiceLivePortrait) — 매니저가 청소를 완료한 뒤에만 보인다.
// 구조: .frame(3:4) > 청소 후 사진(바닥) + 청소 전 사진(위, 막대 위치까지만 보이게 잘림) + 막대·손잡이 + 라벨 2개 + 숨긴 range input.
// 3:4 는 매니저 앱 CameraFrame 과 같은 비율이다 — 매니저가 찍은 화면 그대로 보인다 (liveClient.capturePhoto 가 같은 비율로 잘라 저장한다).
// 손가락으로 끄는 것은 .frame 의 pointer 이벤트로, 키보드·스크린리더는 숨긴 <input type="range">(slider 역할)로 움직인다.
import { useRef, useState } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './BeforeAfterSlider.module.css';

/**
 * @param {object} props
 * @param {string} props.beforeSrc 청소 전 사진 주소
 * @param {string} props.afterSrc 청소 후 사진 주소
 * @param {string} props.location 위치 이름 (예: '거실 소파 앞') — 슬라이더 이름과 사진 설명에 쓴다
 * @param {string} [props.beforeLabel='청소 전'] 왼쪽 위 라벨
 * @param {string} [props.afterLabel='청소 후'] 오른쪽 위 라벨
 * @param {number} [props.initial=50] 처음 막대 위치 (0~100, 왼쪽에서부터 %)
 * @param {string} [props.className]
 */
export default function BeforeAfterSlider({ beforeSrc, afterSrc, location, beforeLabel = '청소 전', afterLabel = '청소 후', initial = 50, onImageError, className }) {
  const [position, setPosition] = useState(initial); // 막대 위치 (0~100 %)
  const frame = useRef(null); // 손가락 위치를 % 로 바꿀 때 틀의 왼쪽 끝·폭을 잰다
  const dragging = useRef(false); // 지금 손가락(마우스)으로 끌고 있는가

  // 화면 x 좌표 → 틀 안 % (0~100 으로 자른다)
  const moveTo = (clientX) => {
    const rect = frame.current?.getBoundingClientRect();
    if (!rect || rect.width === 0) return;
    setPosition(Math.min(100, Math.max(0, ((clientX - rect.left) / rect.width) * 100)));
  };
  // 누르면 그 자리로 막대를 옮기고 끌기 시작한다. setPointerCapture: 손가락이 틀 밖으로 나가도 계속 move 를 받는다
  const down = (e) => {
    dragging.current = true;
    e.currentTarget.setPointerCapture?.(e.pointerId);
    moveTo(e.clientX);
  };
  const move = (e) => {
    if (dragging.current) moveTo(e.clientX);
  };
  const up = () => {
    dragging.current = false;
  };

  const rounded = Math.round(position);
  return (
    <div
      ref={frame}
      className={cx(styles.frame, className)}
      // 막대 위치를 CSS 변수로 넘긴다 — 잘림(clip-path)·막대·손잡이가 모두 이 값을 쓴다. 색·글꼴이 아니라 위치 값이다
      style={{ '--position': `${position}%` }}
      onPointerDown={down}
      onPointerMove={move}
      onPointerUp={up}
      onPointerCancel={up}
    >
      <img src={afterSrc} alt={`${location} ${afterLabel}`} onError={onImageError} className={styles.image} draggable={false} />
      <img src={beforeSrc} alt={`${location} ${beforeLabel}`} onError={onImageError} className={cx(styles.image, styles.before)} draggable={false} />
      <span className={styles.divider} aria-hidden="true">
        <span className={styles.handle}>
          <span className={cx('text-caption-l-bold', styles.arrows)}>‹ ›</span>
        </span>
      </span>
      <span className={cx('text-caption-l-bold', styles.label, styles.labelBefore)} aria-hidden="true">{beforeLabel}</span>
      <span className={cx('text-caption-l-bold', styles.label, styles.labelAfter)} aria-hidden="true">{afterLabel}</span>
      {/* 키보드(←→)·스크린리더용. 눈에는 보이지 않고 틀 전체를 덮지도 않는다 — 끌기는 위 pointer 이벤트가 맡는다 */}
      <input
        type="range"
        min={0}
        max={100}
        step={1}
        value={rounded}
        onChange={(e) => setPosition(Number(e.target.value))}
        className={styles.input}
        aria-label={`${location} 청소 전후 비교`}
        aria-valuetext={`${beforeLabel} ${rounded}% · ${afterLabel} ${100 - rounded}%`}
      />
    </div>
  );
}
