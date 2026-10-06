// Figma: Manager/ChecklistItem (2114:2238) — State=Checked|Unchecked + Label TEXT. 높이 44, 체크박스 24.
// 매니저 앱 작업 화면의 체크리스트 한 줄. JobInProgress 는 눌러서 완료 표시(onChange),
// JobDetail 의 청소 범위는 readOnly 로 보기만 한다(부모가 role="list" 로 감싼다).
// 편집형 구조는 Checkbox 와 같다: 숨긴 진짜 체크박스 + 모양을 그리는 .box + 라벨을 <label> 로 감싼다.
import { useId } from 'react';
import CheckIcon from '../../icons/CheckIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ChecklistItem.module.css';

/**
 * @param {object} props
 * @param {string} props.label (Figma "Label")
 * @param {boolean} props.checked State=Checked
 * @param {(checked: boolean) => void} [props.onChange]
 * @param {boolean} [props.disabled]
 * @param {boolean} [props.readOnly] 표시 전용 (Job/Detail 의 청소 범위). 체크박스 대신 목록 항목으로 렌더
 * @param {string} [props.className]
 */
// 체크리스트 한 줄. 제어 컴포넌트라 체크 상태는 부모가 checked 로 내려 주고, 누르면 onChange(새 값) 만 부른다.
export default function ChecklistItem({ label, checked, onChange, disabled = false, readOnly = false, className }) {
  // useId: React 가 겹치지 않는 id 를 만들어 준다. label 의 htmlFor 와 input 의 id 를 잇는 데 쓴다.
  // 훅은 조건문보다 먼저 불러야 해서(React 규칙) id 가 필요 없는 readOnly 일 때도 여기서 부른다.
  const id = useId();
  // 보기 전용: 누를 수 없으니 체크박스를 두지 않는다. 스크린리더에는 목록 항목으로 읽히고,
  // 체크된 항목은 이름 끝에 " (포함)" 을 붙여 색이 아닌 글로도 알린다.
  // input 이 없으니 체크 색은 input:checked 대신 .boxChecked 클래스로 칠한다.
  if (readOnly) {
    return (
      <div className={cx(styles.item, styles.readOnly, className)} role="listitem" aria-label={`${label}${checked ? ' (포함)' : ''}`}>
        <span className={cx(styles.box, checked && styles.boxChecked)} aria-hidden="true">
          {checked && <CheckIcon />}
        </span>
        <span className={cx('text-body-m', styles.label)}>{label}</span>
      </div>
    );
  }
  // 편집 가능: 체크 상태는 숨긴 input 이 갖고, .box 는 CSS 의 input:checked + .box 로 칠해진다.
  return (
    <label htmlFor={id} className={cx(styles.item, disabled && styles.disabled, className)}>
      <input
        id={id}
        type="checkbox"
        className={styles.input}
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange && onChange(e.target.checked)}
      />
      <span className={styles.box} aria-hidden="true">
        {checked && <CheckIcon />}
      </span>
      <span className={cx('text-body-m', styles.label)}>{label}</span>
    </label>
  );
}
