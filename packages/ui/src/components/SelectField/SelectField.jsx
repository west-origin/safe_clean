// Figma: Booking/Frequency › DateTimeField (2055:1737, 우측 Icon/Calendar 29, 패딩 0/38/0/16) · Booking/Step2 › OptionSelectField (2056:1737, 우측 Chevron, 패딩 0/20/0/16)
// 346×55, 1px border/default, radius 10. 값이 없으면 placeholder(Heading/S text/secondary), 있으면 값(text/primary). 누르면 선택 화면으로 간다.
// 입력창처럼 보이지만 실제로는 버튼이다. 값을 직접 입력받지 않고, 누르면 부모가 준 onClick 이 선택 화면으로 보낸다.
// 예: 고객 앱 예약 플로우 BookingFrequency(날짜/시간, icon="calendar")·BookingStep2(옵션, 기본 chevron).
import CalendarIcon from '../../icons/CalendarIcon.jsx';
import ChevronRightTinyIcon from '../../icons/ChevronRightTinyIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SelectField.module.css';

/**
 * 선택 칸 한 줄. 누르면 onClick 만 부르고, 고른 값은 부모가 value 로 다시 넘겨준다.
 * @param {object} props
 * @param {string} props.placeholder (Figma "Placeholder")
 * @param {string} [props.value] 선택된 값 문구. 없으면 placeholder 표시
 * @param {'calendar'|'chevron'} [props.icon='chevron'] 우측 아이콘 (DateTimeField / OptionSelectField)
 * @param {() => void} [props.onClick]
 * @param {boolean} [props.disabled] 선행 입력이 완료되기 전 이동 차단
 * @param {string} [props.className]
 */
export default function SelectField({ placeholder, value, icon = 'chevron', onClick, disabled = false, className }) {
  // styles[icon]: icon 이 'calendar' 면 .calendar(오른쪽 패딩 38)가 붙는다. 'chevron' 클래스는 CSS 에 없어 아무것도 안 붙는다.
  // 글자가 길면 한 줄에서 말줄임(…)으로 자른다 (.text).
  return (
    <button type="button" className={cx(styles.field, styles[icon], className)} onClick={onClick} disabled={disabled}>
      <span className={cx('text-heading-s', styles.text, value ? styles.value : styles.placeholder)}>{value || placeholder}</span>
      {icon === 'calendar' ? <CalendarIcon className={styles.icon} /> : <ChevronRightTinyIcon className={styles.icon} />}
    </button>
  );
}
