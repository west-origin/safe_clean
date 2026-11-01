// 2026-10-31 사용자 첨부 '예약 할 주소를 선택해 주세요.' 시안의 저장 주소 한 줄 — Figma 에 없음 (코드에서 정함).
// 왼쪽 별칭(굵게) + 주소(끝에 브랜드 색 '수정'), 오른쪽 원형 라디오(RadioOption card 와 같은 모양).
// 줄 전체가 <label> 이라 어디를 눌러도 고른다. 안의 '수정' 버튼은 따로 눌린다 (label 안 버튼은 라디오를 바꾸지 않는다).
import { cx } from '../../utils/cx.js';
import styles from './AddressOption.module.css';

/**
 * @param {object} props
 * @param {string} props.name 같은 묶음 라디오끼리 같은 name
 * @param {string} props.label 주소 별칭 (예: 집)
 * @param {string} props.address 화면에 보일 주소 한 줄
 * @param {boolean} props.selected
 * @param {() => void} [props.onSelect]
 * @param {() => void} [props.onEdit] 있으면 주소 끝에 '수정' 버튼
 * @param {string} [props.className]
 */
export default function AddressOption({ name, label, address, selected, onSelect, onEdit, className }) {
  return (
    <label className={cx(styles.option, className)}>
      {/* 라디오가 label 안 첫 입력 요소여야 label 이 이것에 붙는다 (뒤의 수정 버튼이 아니라) */}
      <input type="radio" name={name} className={styles.input} checked={selected} onChange={onSelect} />
      <span className={styles.text}>
        <span className={cx('text-heading-s', styles.label)}>{label}</span>
        <span className={cx('text-body-m', styles.address)}>
          {address}
          {onEdit && (
            <>
              {' '}
              <button type="button" className={cx('text-body-m', styles.edit)} onClick={onEdit} aria-label={`${label} 주소 수정`}>
                수정
              </button>
            </>
          )}
        </span>
      </span>
      <span className={styles.ring} aria-hidden="true" />
    </label>
  );
}
