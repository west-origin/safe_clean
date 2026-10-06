// Figma: Auth/LoginPhone › Field/Phone (2062:1814) · Auth/ProfileInfo-* › Field/PhoneVerify (2061:1822)
// 라벨 + [Input/Prefix "010" 90×50] [Input/Number(가로 채움) + Icon/TogglePassword] [Button/Verify 70×50 — verify 일 때만]
//
// Figma 프레임       코드
// Field/Phone       labelStyle="heading" (Heading/S, 라벨 간격 6, 칸 간격 10)
// Field/PhoneVerify labelStyle="card" (Title/Card, 라벨 간격 5, 칸 간격 15) + verify
// Button/Verify     ProfileInfo-Phone 은 흰 배경+테두리, ProfileInfo-Social 은 brand 채움 → verifyActive 로 구분 (PROGRESS.md 판단 항목)
// Icon/TogglePassword  번호 가리기 토글 (aria-pressed). Figma 는 placeholder 바로 뒤에 두었지만 코드는 입력창 오른쪽 끝에 둔다
//
// 휴대폰 번호 입력칸. 앞자리 "010" 은 칸 밖에 고정해 보여 주고, 입력칸에는 나머지 번호만 받는다.
// 예: 로그인(고객 LoginPhone)은 입력만, 회원가입 정보 입력(고객 ProfileInfo)은 verify 로 "인증하기" 버튼까지 쓴다.
// disabled 로 보여 주기만 하는 곳도 있다 (예: 고객 앱 내 정보 수정 EditProfile).
// 인증 문자 보내기·재전송 대기·"인증완료" 는 모두 화면이 처리한다: 보내기는 onVerify, 버튼 문구는 verifyLabel,
// 대기 중 막기는 verifyDisabled 로 넘긴다.
import { useId, useState } from 'react';
import EyeIcon from '../../icons/EyeIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './PhoneField.module.css';

/**
 * 앞자리 010 은 칸 밖에 있는데 사람들은 "010-1234-5678" 을 통째로 붙여 넣거나 쳐서 "010 | 010-1234-5678" 로 보였다.
 * 숫자만 남기고, 9자리 이상이 010 으로 시작하면 010 을 뗀다 (직접 사용 점검 U3). 서버(toE164)도 010 을 붙인 11자리를 받는다.
 * '+82 10-1234-5678' 은 먼저 국내 형식(010…)으로 바꾼다. 그래도 8자리를 넘으면 **자르지 않는다** — 예전에는 앞 8자리만 남겨
 * 모르는 사람 번호(82101234)로 인증 문자가 갔다. 넘친 번호는 화면에 그대로 보이고 서버가 거절한다 (자체 점검 #50)
 * 다만 서버는 010 으로 시작하는 11자리에서만 010 을 떼고 +82 는 바꾸지 않는다 — +82 정리는 이 함수에서만 한다.
 */
// 입력: 사용자가 친 글자 그대로(raw). 결과: 앞자리 010 을 뺀 숫자 문자열.
const normalizePhoneRest = (raw) => {
  let digits = String(raw ?? '').replace(/\D/g, '');
  // 82 로 시작하고 11자리 이상일 때만 국가번호로 본다. 그래서 8자리 나머지 번호가 82 로 시작해도 그대로 남는다.
  if (digits.length >= 11 && digits.startsWith('82')) {
    digits = digits.slice(2);
    if (!digits.startsWith('0')) digits = `0${digits}`; // +82 10… → 010…, +82 010… 도 010…
  }
  // 8자리 이하면 그 자체가 나머지 번호일 수 있어(예: '0100') 010 을 떼지 않는다.
  // 9자리 이상이어야 앞자리 010 까지 함께 친 것으로 본다.
  if (digits.length > 8 && digits.startsWith('010')) digits = digits.slice(3);
  return digits;
};

/**
 * @param {object} props
 * @param {string} props.label (Figma "FieldLabel")
 * @param {'card'|'heading'} [props.labelStyle='card'] card=Title/Card(ProfileInfo), heading=Heading/S(LoginPhone)
 * @param {string} [props.prefix='010'] 고정 앞자리 (Figma "Input/Prefix". 선택 UI 는 Figma 에 없음)
 * @param {string} [props.placeholder='123456']
 * @param {string} [props.value] 앞자리를 뺀 나머지 번호 (제어 컴포넌트)
 * @param {(e: import('react').ChangeEvent<HTMLInputElement>) => void} [props.onChange] e.target.value 는 이미 정리된 나머지 번호다 (normalizePhoneRest)
 * @param {boolean} [props.verify] "인증하기" 버튼 표시 (Figma Field/PhoneVerify)
 * @param {boolean} [props.verifyActive] 인증 버튼을 brand 채움으로 (ProfileInfo-Social 모양)
 * @param {() => void} [props.onVerify] 인증 버튼을 눌렀을 때 (문자 보내기는 화면이 한다)
 * @param {string} [props.verifyLabel='인증하기']
 * @param {boolean} [props.verifyDisabled] 인증 버튼만 비활성 (재전송 대기 중) — 입력란은 그대로
 * @param {boolean} [props.disabled] 입력칸·번호 가리기·인증 버튼을 모두 막는다
 * @param {string} [props.id] 생략하면 자동 생성
 * @param {string} [props.className]
 * 그 밖의 props(name, maxLength, autoComplete …)는 <input> 에 그대로 전달된다.
 * 입력칸 값은 부모가 들고 있고(value/onChange), 이 컴포넌트가 직접 들고 있는 것은 "번호 가리기" 켜짐 여부뿐이다.
 */
export default function PhoneField({
  label,
  labelStyle = 'card',
  prefix = '010',
  placeholder = '123456',
  value,
  onChange,
  verify = false,
  verifyActive = false,
  onVerify,
  verifyLabel = '인증하기',
  verifyDisabled = false,
  disabled = false,
  id,
  className,
  ...inputProps
}) {
  // useId: 한 화면에 칸이 여러 개여도 겹치지 않는 id 를 React 가 만들어 준다. label 의 htmlFor 와 input 을 잇는 데 쓴다.
  const autoId = useId();
  const inputId = id ?? `phone-${autoId}`;
  const [masked, setMasked] = useState(false);

  // 앞자리 "010" 칸에 id 를 주고 input 의 aria-describedby 로 이어서, 화면 읽기 도구가 입력칸을 읽을 때 앞자리도 함께 읽는다.
  // 번호 가리기·인증 버튼은 입력칸과 같은 disabled 를 따르고, 인증 버튼은 verifyDisabled 로 따로 막을 수도 있다.
  // onChange 는 친 글자를 normalizePhoneRest 로 정리해 e.target.value 에 다시 넣은 뒤 부모에게 넘긴다 → 부모는 정리된 값만 받는다.
  // {...inputProps} 가 맨 뒤라서 부모가 autoComplete·type 등을 넘기면 위에 적힌 기본값을 덮어쓴다.
  return (
    <div className={cx(styles.field, styles[labelStyle], verify && styles.withVerify, disabled && styles.disabled, className)}>
      <label htmlFor={inputId} className={cx(labelStyle === 'heading' ? 'text-heading-s' : 'text-title-card', styles.label)}>
        {label}
      </label>
      <div className={styles.row}>
        <span className={cx('text-title-card', styles.prefix)} id={`${inputId}-prefix`}>
          {prefix}
        </span>
        <div className={styles.numberBox}>
          <input
            id={inputId}
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            aria-describedby={`${inputId}-prefix`}
            className={cx('text-title-card', styles.input, masked && styles.masked)}
            placeholder={placeholder}
            value={value}
            onChange={(e) => {
              e.target.value = normalizePhoneRest(e.target.value); // 화면은 e.target.value 를 그대로 쓴다
              onChange?.(e);
            }}
            disabled={disabled}
            {...inputProps}
          />
          <button
            type="button"
            className={styles.toggle}
            onClick={() => setMasked((m) => !m)}
            aria-pressed={masked}
            aria-label="번호 가리기"
            disabled={disabled}
          >
            <EyeIcon />
          </button>
        </div>
        {verify && (
          <button type="button" className={cx('text-title-small', styles.verify, verifyActive && styles.verifyActive)} onClick={onVerify} disabled={disabled || verifyDisabled}>
            {verifyLabel}
          </button>
        )}
      </div>
    </div>
  );
}
