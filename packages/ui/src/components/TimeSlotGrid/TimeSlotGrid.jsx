// Figma: Booking/DateTime › TimePanel (2058:1742) — 393×337, 패딩 20/23/8/24.
//   "방문 시간 선택" Heading/S → "오전" Body/Bold(상 33) → TimeSlotGrid/AM(상 6, 줄바꿈, 간격 14, 좌 6) → "오후"(상 18) → TimeSlotGrid/PM(상 7)
//   TimeSlot 104×44 radius 10, 1px border, Label/Default. Selected = brand/primary 배경 + 흰 글자
// 방문 시간 고르기. 쓰는 곳: 고객 앱 청소 예약 BookingDateTime(grouped, 시간 목록은 api 의 getTimeSlots),
// 수리 예약 RepairDateTime(flat, 시간 목록은 catalog.timeSlots). 선택 값은 부모가 들고 있고 누르면 onChange(시간) 만 부른다.
// 시간 칸은 button 에 role="radio" 를 붙여 스크린리더에 "하나만 고르는 선택지"로 알린다.
import { cx } from '../../utils/cx.js';
import styles from './TimeSlotGrid.module.css';

/**
 * @param {object} props
 * @param {{ am: string[], pm: string[] }} props.slots 시간 문구 (Figma: 09:00~12:00 / 13:00~18:00)
 * @param {string} [props.value] 선택한 시간 (예: '11:00')
 * @param {(time: string) => void} [props.onChange]
 * @param {(time: string) => boolean} [props.isDisabled] 현재 선택 날짜에서 막을 시간
 * @param {string} [props.title='방문 시간 선택']
 * @param {'grouped'|'flat'} [props.layout='grouped'] grouped=오전/오후 묶음(청소 예약), flat=제목 아래 한 격자(수리 예약 1:398 — 줄바꿈, 간격 8/10)
 * @param {string} [props.className]
 */
// layout 에 따라 오전/오후 두 묶음(grouped) 또는 한 격자(flat)로 시간 칸을 그린다. value 와 같은 칸이 선택 상태다.
export default function TimeSlotGrid({ slots, value, onChange, isDisabled = () => false, title = '방문 시간 선택', layout = 'grouped', className }) {
  // grouped 에서 쓰는 한 묶음: 소제목(h3, "오전"/"오후") + 시간 칸 격자.
  // groupClass(styles.am / styles.pm)로 소제목 위 여백을 고르고, 같은 값으로 격자 위 여백(gridAm / gridPm)도 고른다.
  const group = (label, list, groupClass) => (
    <>
      <h3 className={cx('text-body-bold', styles.groupLabel, groupClass)}>{label}</h3>
      <div className={cx(styles.grid, groupClass === styles.am ? styles.gridAm : styles.gridPm)} role="radiogroup" aria-label={`${label} 시간`}>
        {list.map((t) => (
          <button key={t} type="button" role="radio" disabled={isDisabled(t)} aria-checked={t === value && !isDisabled(t)} className={cx('text-label-default', styles.slot, t === value && !isDisabled(t) && styles.selected)} onClick={() => onChange && onChange(t)}>
            {t}
          </button>
        ))}
      </div>
    </>
  );
  // flat: 오전·오후 소제목 없이 두 목록을 이어 붙여 한 격자로 그린다 (수리 예약)
  if (layout === 'flat') {
    return (
      <section className={cx(styles.flat, className)} aria-label={title}>
        <h2 className={cx('text-heading-s', styles.title)}>{title}</h2>
        <div className={styles.flatGrid} role="radiogroup" aria-label="방문 시간">
          {[...slots.am, ...slots.pm].map((t) => (
            <button key={t} type="button" role="radio" disabled={isDisabled(t)} aria-checked={t === value && !isDisabled(t)} className={cx('text-label-default', styles.slot, t === value && !isDisabled(t) && styles.selected)} onClick={() => onChange && onChange(t)}>
              {t}
            </button>
          ))}
        </div>
      </section>
    );
  }
  // grouped(기본): 제목 아래 오전 묶음 → 오후 묶음 (청소 예약)
  return (
    <section className={cx(styles.panel, className)} aria-label={title}>
      <h2 className={cx('text-heading-s', styles.title)}>{title}</h2>
      {group('오전', slots.am, styles.am)}
      {group('오후', slots.pm, styles.pm)}
    </section>
  );
}
