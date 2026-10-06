// Figma: Auth/ProfileAddress › Field/RoadAddress (2062:1802) — 라벨(Title/Card) + 돋보기 아이콘(30) 이 앞에 있는 입력창(345×50, Label/M).
// TextField 와 달리 라벨 간격 4, 좌 패딩 7, 글꼴 Label/M 이라 분리했다. 주소 검색 화면은 Figma 인증 플로우에 없어 일반 입력으로 동작한다.
// filled: Booking/AddressSearch › SearchField (2057:1739) — 라벨 없음, surface/field 배경, 테두리 없음, 좌 14 / 우 16
// 예: 고객 앱 회원가입 주소 입력(ProfileAddress)은 라벨형, 예약 주소 검색(BookingAddressSearch)은 filled 형.
// 입력값은 부모가 들고 있다(value + onChange).
import { useId } from 'react';
import SearchIcon from '../../icons/SearchIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SearchField.module.css';

/**
 * @param {object} props
 * @param {string} [props.label] (Figma "FieldLabel"). filled 에서는 없음 → ariaLabel 로 접근성 라벨
 * @param {boolean} [props.filled] 예약 주소 검색형 (배경 surface/field, 테두리 없음)
 * @param {string} [props.ariaLabel] label 이 없을 때 input 의 aria-label
 * @param {string} [props.placeholder]
 * @param {string} [props.value]
 * @param {(e: import('react').ChangeEvent<HTMLInputElement>) => void} [props.onChange]
 * @param {boolean} [props.disabled]
 * @param {string} [props.id] 생략하면 자동 생성
 * @param {string} [props.className]
 * 그 밖의 props(name, autoComplete, onFocus …)는 <input> 에 그대로 전달된다.
 */
export default function SearchField({ label, filled = false, ariaLabel, placeholder, value, onChange, disabled = false, id, className, ...inputProps }) {
  // useId: 화면에 같은 컴포넌트가 여러 개 있어도 겹치지 않는 id 를 React 가 만들어 준다.
  // 이 id 로 <label htmlFor> 와 <input id> 를 이어, 라벨을 누르면 입력칸에 초점이 가고 화면 읽기 도구가 라벨을 읽는다.
  const autoId = useId();
  const inputId = id ?? `search-${autoId}`;
  // 라벨이 있으면 <label> 이 이름이 되므로 aria-label 은 붙이지 않는다 (filled 형처럼 라벨이 없을 때만 ariaLabel).
  // type="search" 라 모바일 키보드에 검색 키가 나올 수 있다. 브라우저가 그리는 지우기(×) 버튼은 CSS 에서 숨긴다.
  return (
    <div className={cx(styles.field, filled && styles.filled, disabled && styles.disabled, className)}>
      {label && (
        <label htmlFor={inputId} className={cx('text-title-card', styles.label)}>
          {label}
        </label>
      )}
      <div className={styles.inputBox}>
        <SearchIcon className={styles.icon} />
        <input id={inputId} type="search" aria-label={label ? undefined : ariaLabel} className={cx('text-label-m', styles.input)} placeholder={placeholder} value={value} onChange={onChange} disabled={disabled} {...inputProps} />
      </div>
    </div>
  );
}
