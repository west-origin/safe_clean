// Figma: Field/Text (2171:3080)
//
// Figma State      코드
// Default          기본
// Focused          :focus-within (입력창에 포커스) → 테두리 brand/primary 1.5px
// Filled           value 가 있으면 글자색 text/primary (placeholder 는 text/secondary)
// Error            error 문자열 → 테두리 status/live 1.5px + 헬퍼 텍스트 + aria-invalid (색만으로 표시하지 않음)
// Disabled         disabled → 라벨·글자 text/disabled, 배경 surface/subtle, 테두리 border/disabled
//
// Success(추가)      Auth/ProfileInfo-Phone › Field/비밀번호 의 HelperText("사용가능한 비밀번호입니다!", Caption/Medium, brand/primary).
//                  Figma 컴포넌트에는 없는 상태라 화면 프레임에서 옮겨 왔다 (PROGRESS.md 판단 항목)
//
// Figma TEXT property: Label → label, Placeholder → placeholder
//
// 라벨 + 한 줄 입력칸 + (있으면) 아래 안내 한 줄. 쓰는 곳: 두 앱의 입력 화면 대부분
// (예: 고객 앱 가입 단계 ProfileInfo, 매니저 앱 Login)과 공용 UI DeleteAccountSheet(비밀번호 확인).
// 아래 안내는 한 번에 하나만 보인다: error > success > hint 순서로 앞의 것이 이긴다. disabled 면 error·success 는 숨긴다.
import { useId } from 'react';
import styles from './TextField.module.css';

/**
 * @param {object} props
 * @param {string} props.label 필드 라벨 (Figma "Label")
 * @param {string} [props.placeholder] (Figma "Placeholder")
 * @param {string} [props.value] 제어 컴포넌트 값
 * @param {(e: import('react').ChangeEvent<HTMLInputElement>) => void} [props.onChange]
 * @param {string} [props.error] 오류 메시지. 값이 있으면 Error 상태 (Figma 기본 문구: "올바른 값을 입력해주세요")
 * @param {string} [props.success] 성공 안내 문구. 값이 있으면 입력창 아래 brand/primary 색 헬퍼 텍스트 (error 가 있으면 error 우선)
 * @param {string} [props.hint] 입력 전부터 보여 주는 안내(예: "8자 이상"). error·success 가 있으면 그쪽이 이긴다 (코드에서 정함 — 사용성 검토 2026-09-22)
 * @param {boolean} [props.disabled]
 * @param {string} [props.id] 생략하면 자동 생성
 * @param {string} [props.type='text'] input type (text, tel, email, number, password …)
 * @param {'titleCard'|'labelM'} [props.textStyle='titleCard'] 입력 글꼴. Field/Text 는 Title/Card, Auth/ProfileHousing › Section/Size 의 Input 은 Label/M
 * @param {string} [props.className] 레이아웃용 추가 클래스
 * 그 밖의 props(name, inputMode, maxLength, autoComplete, onBlur …)는 <input> 에 그대로 전달된다.
 */
// 제어 컴포넌트: 값은 부모가 value 로 내려 주고, 입력하면 onChange(이벤트) 를 부른다. 서버 호출·검사는 부모가 한다.
export default function TextField({
  label,
  placeholder,
  value,
  onChange,
  error,
  success,
  hint,
  disabled = false,
  id,
  type = 'text',
  textStyle = 'titleCard',
  className,
  ...inputProps
}) {
  // useId: React 가 컴포넌트마다 겹치지 않는 id 를 만들어 준다. label 의 htmlFor 와 input 의 id 를 잇는 데 쓴다.
  const autoId = useId();
  const inputId = id ?? `field-${autoId}`;
  // 아래 안내 문구의 id. input 의 aria-describedby 로 이어 스크린리더가 입력칸과 함께 읽게 한다.
  const helperId = `${inputId}-helper`;
  // 어느 안내를 보일지 고른다 (error > success > hint, disabled 면 error·success 는 없음). 셋 중 많아야 하나만 참이다.
  const hasError = Boolean(error) && !disabled;
  const hasSuccess = !hasError && Boolean(success) && !disabled;
  const hasHint = !hasError && !hasSuccess && Boolean(hint);

  // 바깥 div 클래스: Error·Disabled 는 CSS 가 이 클래스를 보고 라벨·입력칸 모양을 바꾼다
  const rootClass = [styles.field, hasError && styles.error, disabled && styles.disabled, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClass}>
      <label htmlFor={inputId} className={`text-title-card ${styles.label}`}>
        {label}
      </label>
      <div className={styles.inputBox}>
        <input
          id={inputId}
          type={type}
          className={`${textStyle === 'labelM' ? 'text-label-m' : 'text-title-card'} ${styles.input}`}
          placeholder={placeholder}
          value={value}
          onChange={onChange}
          disabled={disabled}
          aria-invalid={hasError || undefined}
          aria-describedby={hasError || hasSuccess || hasHint ? helperId : undefined}
          {...inputProps}
        />
      </div>
      {/* 안내 문구. role="alert" 는 나타나자마자 스크린리더가 읽고, role="status" 는 하던 말을 끊지 않고 읽는다 */}
      {hasError && (
        <p id={helperId} role="alert" className={`text-body-xs ${styles.helper}`}>
          {error}
        </p>
      )}
      {hasSuccess && (
        <p id={helperId} role="status" className={`text-caption-medium ${styles.helperSuccess}`}>
          {success}
        </p>
      )}
      {hasHint && (
        <p id={helperId} className={`text-caption-medium ${styles.helperHint}`}>
          {hint}
        </p>
      )}
    </div>
  );
}
