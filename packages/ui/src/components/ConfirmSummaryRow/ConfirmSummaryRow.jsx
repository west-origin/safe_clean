// Figma(수리 예약): 예약 요약 항목(1:286) — 아이콘(텍스트 이모지 22 또는 InfoIcon 20) → [항목(Caption/L secondary) / 값(Body/Bold) / 보조 정보(Caption/L secondary)] 간격 3 → "수정 ›"(Caption/Medium), 세로 간격 12
// 고객 앱 수리 예약의 마지막 확인 화면(RepairConfirm)에서 요약을 한 항목씩 보여 준다 (예: 서비스, 주소).
// "수정 ›" 을 누르면 onEdit 을 부른다. 어느 단계로 갈지(RepairConfirm 은 그 단계만 고치고 확인으로 복귀)는 화면이 정한다.
import { cx } from '../../utils/cx.js';
import styles from './ConfirmSummaryRow.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.icon 이모지 문자열(Figma 텍스트) 또는 아이콘 컴포넌트
 * @param {string} props.label 항목 이름 (예: "서비스")
 * @param {string} props.value
 * @param {string} [props.sub] 보조 정보
 * @param {() => void} [props.onEdit] "수정 ›"
 * @param {string} [props.className]
 */
// 요약 한 항목. 아이콘은 꾸밈이라 스크린리더에서 숨기고, onEdit 이 없으면 수정 버튼을 그리지 않는다.
export default function ConfirmSummaryRow({ icon, label, value, sub, onEdit, className }) {
  return (
    <div className={cx(styles.row, className)}>
      {/* 이모지(문자열)는 글자라서 Heading/S 글꼴 크기로 키우고, 아이콘 컴포넌트는 자기 크기 그대로 둔다 */}
      <span className={cx(typeof icon === 'string' && 'text-heading-s', styles.icon)} aria-hidden="true">{icon}</span>
      <div className={styles.content}>
        <span className={cx('text-caption-l', styles.label)}>{label}</span>
        <span className={cx('text-body-bold', styles.value)}>{value}</span>
        {sub && <span className={cx('text-caption-l', styles.sub)}>{sub}</span>}
      </div>
      {/* 화면에 "수정 ›" 버튼이 여러 개라 스크린리더용 이름에 항목 이름을 붙여 구분한다 (예: "서비스 수정") */}
      {onEdit && (
        <button type="button" className={cx('text-caption-medium', styles.edit)} onClick={onEdit} aria-label={`${label} 수정`}>
          수정 ›
        </button>
      )}
    </div>
  );
}
