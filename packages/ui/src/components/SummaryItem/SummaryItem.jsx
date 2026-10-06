// Figma: Booking/Confirm › SummaryCard › SummaryRow/* (2039:1657 …) — 아이콘 50 + Body(제목 Title/Small, 값 Caption/L-Bold, 부가 Label/XS text/secondary, 간격 1) + EditLink "수정하기    >" (Caption/Medium, 71×44, 우측 상단 -13 절대 위치)
// Options/Notes 는 Body 간격 8 + Chip 묶음 → children 으로, Requests 는 간격 12 + 회색 상자(110) → note 로 받는다
// 쓰는 곳: 고객 앱 청소 예약 최종 확인 화면(screens/booking/BookingConfirm.jsx)의 항목 한 줄씩.
// 이름이 비슷한 SummaryRow(라벨 + 값 한 줄)와는 다른 컴포넌트다.
import { cx } from '../../utils/cx.js';
import styles from './SummaryItem.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string, width: number, height: number}} props.icon ASSETS.confirmIcons.*
 * @param {string} props.title (예: "서비스 종류")
 * @param {string} [props.value] Caption/L-Bold text/primary
 * @param {string} [props.subValue] Label/XS text/secondary
 * @param {import('react').ReactNode} [props.children] 칩 묶음 등. value·subValue 아래에 이어 그린다 (요청사항 상자는 note 로)
 * @param {'chips'|'note'} [props.bodyGap] children 일 때 제목 ↔ 내용 간격: chips=8, note=12
 * @param {string} [props.note] 요청사항 문구 — 회색 상자(surface/subtle, 110) 안에 표시
 * @param {() => void} [props.onEdit] "수정하기    >"
 * @param {string} [props.className]
 */
// 왼쪽 그림 아이콘(WebP 1x/2x/3x, 꾸밈이라 alt="") + 오른쪽 내용 + (onEdit 이 있으면) 오른쪽 위 "수정하기" 버튼.
// 내용은 value·subValue 글줄, children(칩 묶음 등), note(회색 상자) 중 넘긴 것만 그린다.
export default function SummaryItem({ icon, title, value, subValue, children, bodyGap = 'chips', note, onEdit, className }) {
  return (
    <div className={cx(styles.row, className)}>
      <img className={styles.icon} src={icon.src} srcSet={icon.srcSet} width={icon.width} height={icon.height} alt="" />
      {/* 제목 ↔ 내용 간격: note 가 있으면 12(gap-note), 아니고 children 이 있으면 bodyGap(8 또는 12), 둘 다 없으면 1(gapText).
          주의: 조건부 자식을 여러 개 넘기면 모두 비어도 children 이 배열이라 참이 된다(예: BookingConfirm 요금 행이 견적을 받기 전) */}
      <div className={cx(styles.body, children || note !== undefined ? styles[`gap-${note !== undefined ? 'note' : bodyGap}`] : styles.gapText)}>
        <p className={cx('text-title-small', styles.title)}>{title}</p>
        {value !== undefined && <p className={cx('text-caption-l-bold', styles.value)}>{value}</p>}
        {subValue !== undefined && <p className={cx('text-label-xs', styles.subValue)}>{subValue}</p>}
        {children}
        {note !== undefined && <p className={cx('text-caption-l', styles.note)}>{note}</p>}
      </div>
      {/* 한 화면에 "수정하기" 버튼이 여러 개라 aria-label 에 항목 제목을 붙여 스크린리더가 구분하게 한다.
          문구 안의 공백 4칸은 CSS 의 white-space: pre 로 그대로 보인다 */}
      {onEdit && (
        <button type="button" className={cx('text-caption-medium', styles.edit)} onClick={onEdit} aria-label={`${title} 수정하기`}>
          {'수정하기    >'}
        </button>
      )}
    </div>
  );
}
