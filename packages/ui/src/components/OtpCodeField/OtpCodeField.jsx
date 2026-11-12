// (코드에서 정함) 인증번호 6칸 입력 — Figma 에 없다. 2026-11-10 사용자 첨부 시안(휴대폰 인증 2번째 화면)의 숫자 칸 6개.
// 실제 입력은 <input> 하나가 받고(붙여넣기·문자 자동 입력 autocomplete="one-time-code" 가 그대로 된다), 칸 6개는 그 값을 나눠 보여 주기만 한다.
// 투명한 input 이 칸들 위에 겹쳐 있다(오버레이) — 어느 칸을 눌러도 같은 입력칸에 포커스가 간다.
import { useId, useState } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './OtpCodeField.module.css';

/**
 * @param {object} props
 * @param {string} props.label 보조기기용 이름 (화면에는 안 보임)
 * @param {string} [props.value=''] 지금까지 입력한 숫자
 * @param {(value: string) => void} [props.onChange] 숫자만 남기고 length 자리로 자른 **문자열**을 넘긴다 (이벤트가 아니다)
 * @param {number} [props.length=6] 칸 수
 * @param {string} [props.error] 오류 문구. 있으면 칸 테두리가 오류색 + aria-invalid + 아래 문구
 * @param {string} [props.timer] 칸 아래 오른쪽에 brand 색으로 보이는 남은 시간 (예: '02:58')
 * @param {boolean} [props.disabled]
 * @param {boolean} [props.autoFocus]
 * @param {string} [props.id]
 * @param {string} [props.className] 레이아웃용
 */
export default function OtpCodeField({ label, value = '', onChange, length = 6, error, timer, disabled = false, autoFocus, id, className }) {
  const autoId = useId();
  const inputId = id ?? `otp-${autoId}`;
  const helperId = `${inputId}-helper`;
  const [focused, setFocused] = useState(false);
  const digits = value.slice(0, length).split('');
  const active = Math.min(digits.length, length - 1); // 다음에 채울 칸 (다 채우면 마지막 칸)
  const hasError = Boolean(error) && !disabled;

  return (
    <div className={cx(styles.field, hasError && styles.error, disabled && styles.disabled, className)}>
      <label htmlFor={inputId} className="sr-only">{label}</label>
      <div className={styles.boxes}>
        {Array.from({ length }, (_, i) => (
          <span
            key={i}
            aria-hidden="true"
            data-state={digits[i] ? 'filled' : focused && i === active ? 'active' : 'empty'}
            className={cx('text-heading-m', styles.box)}
          >
            {digits[i] ?? ''}
          </span>
        ))}
        <input
          id={inputId}
          className={styles.input}
          value={value}
          onChange={(e) => onChange?.(e.target.value.replace(/\D/g, '').slice(0, length))}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete="one-time-code"
          maxLength={length}
          disabled={disabled}
          autoFocus={autoFocus}
          aria-invalid={hasError || undefined}
          aria-describedby={hasError ? helperId : undefined}
        />
      </div>
      {(hasError || timer) && (
        <div className={styles.footer}>
          {hasError && <p id={helperId} role="alert" className={cx('text-body-xs', styles.helper)}>{error}</p>}
          {timer && <p className={cx('text-body-default', styles.timer)} aria-live="off">{timer}</p>}
        </div>
      )}
    </div>
  );
}
