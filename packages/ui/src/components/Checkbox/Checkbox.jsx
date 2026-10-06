// Figma: Auth/TermsConsent › AgreeAllRow (2063:1804) · TermRow › Checkbox (2075:1862)
// 원형 체크박스 25px. Checked = text/primary 채움 + 흰 체크, Unchecked = border/default 1.8 테두리 + 회색 체크.
// 시각 높이가 25 라 터치 영역은 ::before 로 44 확보. 라벨 글꼴: bold(전체 동의하기) = Title/Small, 기본 = Body/S.
// Auth/ProfileHousing › CheckRow (2064:1814) "기억이 안나요": 라벨이 앞(reverse), Label/M, 연한 회색(muted).
// 쓰는 곳 예: 고객 앱 TermsConsent(전체 동의)·ProfileHousing(기억이 안나요), 공용 UI TermRow(약관 한 줄).
// 구조: <label> 이 숨긴 진짜 <input type="checkbox"> 와 모양을 그리는 원(.box)·문구를 감싼다.
// 그래서 원이든 문구든 어디를 눌러도 토글되고, 스크린리더·키보드는 진짜 체크박스를 그대로 쓴다.
import { useId } from 'react';
import CheckMarkIcon from '../../icons/CheckMarkIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './Checkbox.module.css';

/**
 * @param {object} props
 * @param {string} props.label 라벨 문구 (Figma 문구 그대로. 줄바꿈은 \n)
 * @param {boolean} props.checked
 * @param {(checked: boolean) => void} [props.onChange]
 * @param {boolean} [props.bold] 라벨을 Title/Small 로 (AgreeAllRow "전체 동의하기")
 * @param {'bodyS'|'titleSmall'|'labelM'} [props.labelStyle] 라벨 글꼴. 생략하면 bold 에 따라 bodyS / titleSmall
 * @param {boolean} [props.muted] 라벨을 연한 회색(원본색 → --raw-checkbox-muted-label)으로 (CheckRow)
 * @param {boolean} [props.reverse] 라벨을 체크박스 앞에 (CheckRow)
 * @param {boolean} [props.disabled]
 * @param {string} [props.id] 생략하면 자동 생성
 * @param {string} [props.className]
 */
// labelStyle 값 → 라벨에 붙일 전역 글꼴 클래스(.text-*) 이름.
const LABEL_CLASS = { bodyS: 'text-body-s', titleSmall: 'text-title-small', labelM: 'text-label-m' };

// 원형 체크박스 + 라벨. 제어 컴포넌트다: 체크 상태는 부모가 checked 로 내려 주고,
// 누르면 onChange(새 체크값) 만 부른다. props 설명은 LABEL_CLASS 위의 JSDoc 이다
// (주의: 그 자리라서 에디터 힌트는 이 함수가 아니라 LABEL_CLASS 에 붙는다).
export default function Checkbox({ label, checked, onChange, bold = false, labelStyle, muted = false, reverse = false, disabled = false, id, className }) {
  // useId: React 가 컴포넌트마다 겹치지 않는 id 를 만들어 준다. label 의 htmlFor 와 input 의 id 를 잇는 데 쓴다.
  const autoId = useId();
  const inputId = id ?? `checkbox-${autoId}`;
  // labelStyle 을 주면 그것을, 없으면 bold 여부로 Title/Small 또는 Body/S 를 고른다.
  const labelClass = LABEL_CLASS[labelStyle ?? (bold ? 'titleSmall' : 'bodyS')];
  return (
    <label htmlFor={inputId} className={cx(styles.item, reverse && styles.reverse, disabled && styles.disabled, className)}>
      <input id={inputId} type="checkbox" className={styles.input} checked={checked} disabled={disabled} onChange={(e) => onChange && onChange(e.target.checked)} />
      {/* 체크 표시는 늘 그리고, 색(Unchecked 회색 / Checked 흰색)만 CSS 의 input:checked + .box 가 바꾼다 */}
      <span className={styles.box} aria-hidden="true">
        <CheckMarkIcon />
      </span>
      <span className={cx(labelClass, styles.label, muted && styles.muted)}>{label}</span>
    </label>
  );
}
