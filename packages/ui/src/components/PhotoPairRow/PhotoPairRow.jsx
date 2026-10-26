// (코드에서 정함 — Figma 에 없음, 2026-10-23 사용자 요청) 청소 사진 한 위치의 줄: [청소 전 사진] 위치·설명 [청소 후 사진 또는 빈 칸] (삭제)
// 쓰는 곳 예: 매니저 앱 청소 진행 화면의 '청소 전 사진' 목록(삭제 가능), 청소 후 사진 화면(눌러서 같은 위치 촬영).
// onClick 이 있으면 사진·글자 부분이 버튼이 된다. 삭제 버튼은 그 옆의 별도 버튼이다(버튼 안에 버튼을 넣지 않는다).
import { cx } from '../../utils/cx.js';
import styles from './PhotoPairRow.module.css';

/**
 * @param {object} props
 * @param {string} props.location 위치 이름 (예: '거실 소파 앞')
 * @param {string} [props.description] 위치 아래 작은 글 (예: '촬영 필요')
 * @param {'default'|'done'|'needed'} [props.tone='default'] 설명 글 색 — done = 완료(초록), needed = 할 일(경고색). 색만이 아니라 글로도 알린다
 * @param {string} props.beforeSrc 청소 전 사진
 * @param {string|null} [props.afterSrc] 청소 후 사진. showAfter 이고 없으면 빈 칸
 * @param {boolean} [props.showAfter=false] 오른쪽에 청소 후 칸을 그린다
 * @param {string} [props.afterPlaceholder='후'] 청소 후 사진이 없을 때 빈 칸 글자
 * @param {() => void} [props.onClick] 줄을 눌렀을 때 (없으면 보기 전용)
 * @param {string} [props.actionLabel] onClick 버튼의 접근성 이름 (없으면 위치 이름)
 * @param {() => void} [props.onRemove] 삭제 버튼을 눌렀을 때 (없으면 버튼 없음)
 * @param {string} [props.removeLabel='삭제'] 삭제 버튼 글자 (접근성 이름은 '<위치> <글자>')
 * @param {boolean} [props.disabled] 두 버튼 모두 잠근다 (요청 중)
 * @param {string} [props.className]
 */
export default function PhotoPairRow({ location, description, tone = 'default', beforeSrc, afterSrc, showAfter = false, afterPlaceholder = '후', onClick, actionLabel, onRemove, removeLabel = '삭제', disabled, className }) {
  const body = (
    <>
      <img src={beforeSrc} alt="" className={styles.thumb} />
      <span className={styles.text}>
        <span className={cx('text-title-small', styles.location)}>{location}</span>
        {description && <span className={cx('text-body-s', styles.description, styles[tone])}>{description}</span>}
      </span>
      {showAfter &&
        (afterSrc ? (
          <img src={afterSrc} alt="" className={styles.thumb} />
        ) : (
          <span className={cx('text-caption-l', styles.thumb, styles.empty)} aria-hidden="true">
            {afterPlaceholder}
          </span>
        ))}
    </>
  );
  return (
    <div className={cx(styles.row, className)}>
      {onClick ? (
        <button type="button" className={cx(styles.main, styles.button)} onClick={onClick} disabled={disabled} aria-label={actionLabel ? `${location} ${actionLabel}` : undefined}>
          {body}
        </button>
      ) : (
        <div className={styles.main}>{body}</div>
      )}
      {onRemove && (
        <button type="button" className={cx('text-body-s', styles.remove)} onClick={onRemove} disabled={disabled} aria-label={`${location} ${removeLabel}`}>
          {removeLabel}
        </button>
      )}
    </div>
  );
}
