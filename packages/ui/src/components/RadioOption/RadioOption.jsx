// Figma: RadioOption (2060:1750) — State=Selected|Default. 346×55, Label 은 인스턴스 오버라이드.
// 한 줄짜리 선택지 버튼(동그라미 체크 + 라벨). 여러 개 중 하나를 고르는 곳에 쓴다.
// 예: 고객 앱 예약 취소 시트의 취소 범위, 매니저 앱 거절 사유 목록. 공용 UI 의 OptionGroup 도 이것을 묶어 쓴다.
// 선택 상태는 부모가 들고 있고(selected), 이 컴포넌트는 "눌렸다" 는 것(onSelect)만 알린다.
import { cx } from '../../utils/cx.js';
import styles from './RadioOption.module.css';

/** Figma › Indicator (2075:1755 Selected / 2075:1758 Default), 25px. path 원본 그대로. */
function Indicator({ selected }) {
  // 선택되면 채운 원, 아니면 테두리만 있는 원. 체크 선은 두 상태 모두 그리고 색만 CSS(.check)가 바꾼다.
  // 뜻은 라벨과 aria-checked 가 전하므로 그림 자체는 보조기기에서 숨긴다(aria-hidden).
  return (
    <svg width="25" height="25" viewBox="0 0 25 25" fill="none" aria-hidden="true" className={styles.indicator}>
      {selected ? (
        <path d="M12.5 25C19.4036 25 25 19.4036 25 12.5C25 5.59644 19.4036 0 12.5 0C5.59644 0 0 5.59644 0 12.5C0 19.4036 5.59644 25 12.5 25Z" fill="currentColor" />
      ) : (
        <path d="M12.5 24.1C18.9065 24.1 24.1 18.9065 24.1 12.5C24.1 6.0935 18.9065 0.9 12.5 0.9C6.0935 0.9 0.9 6.0935 0.9 12.5C0.9 18.9065 6.0935 24.1 12.5 24.1Z" stroke="currentColor" strokeWidth="1.8" />
      )}
      <path d="M6.8 12.9L10.7 16.6L18.2 8.8" className={styles.check} strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/**
 * @param {object} props
 * @param {string} props.label
 * @param {string} [props.description] 라벨 아래 한 줄 설명 (Figma 에 없음 — 무엇을 고르는 것인지 먼저 알려 주려고, 사용성 검토 2026-09-22)
 * @param {boolean} props.selected State=Selected
 * @param {() => void} [props.onSelect]
 * @param {string} [props.className]
 * 누르면 onSelect 만 부른다. 선택을 바꾸는 것은 부모의 몫이라, 매니저 근무 일정의 "하루 종일" 처럼
 * 같은 버튼을 다시 누르면 꺼지는 토글로 쓰는 곳도 있다.
 */
export default function RadioOption({ label, description, selected, onSelect, className }) {
  // 모양은 버튼이지만 role="radio" + aria-checked 로 보조기기가 "선택됨/선택 안 됨" 라디오로 읽게 한다.
  // 묶음(role="radiogroup")과 그룹 이름은 부르는 쪽이 감싸서 붙인다.
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      className={cx(styles.option, description && styles.withDescription, selected && styles.selected, className)}
      onClick={onSelect}
    >
      <Indicator selected={selected} />
      <span className={styles.text}>
        <span className={cx('text-heading-s', styles.label)}>{label}</span>
        {description && <span className={cx('text-body-s', styles.description)}>{description}</span>}
      </span>
    </button>
  );
}
