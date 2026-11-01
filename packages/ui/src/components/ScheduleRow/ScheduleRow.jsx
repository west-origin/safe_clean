// 2026-10-31 사용자 첨부 '날짜/시간을 선택해 주세요.' 시안의 한 줄 — Figma 에 없음 (코드에서 정함).
// 왼쪽: 고른 값(크게) + 회색 설명, 아직 안 골랐으면 회색 자리 글자(예: 방문 일)만. 오른쪽: 흰 알약 버튼(변경/선택).
import Button from '../Button/Button.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ScheduleRow.module.css';

/**
 * @param {object} props
 * @param {string} [props.value] 고른 값 (예: '2시간', '11월 3일(화)'). 비었으면 placeholder 를 회색으로
 * @param {string} props.caption 항목 이름 (예: '서비스 시간', '방문 일') — 값이 있을 때 그 아래 회색, 없을 때 크게 회색
 * @param {string} props.actionLabel 오른쪽 버튼 글자 (예: '변경', '선택')
 * @param {() => void} [props.onAction]
 * @param {string} [props.className]
 */
export default function ScheduleRow({ value, caption, actionLabel, onAction, className }) {
  return (
    <div className={cx(styles.row, className)}>
      <div className={styles.text}>
        {value ? (
          <>
            <span className={cx('text-heading-m', styles.value)}>{value}</span>
            <span className={cx('text-body-default', styles.caption)}>{caption}</span>
          </>
        ) : (
          <span className={cx('text-heading-m', styles.placeholder)}>{caption}</span>
        )}
      </div>
      <Button variant="outlineGray" chevron={false} className={styles.action} onClick={onAction} aria-label={`${caption} ${actionLabel}`}>
        {actionLabel}
      </Button>
    </div>
  );
}
