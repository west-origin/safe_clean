// (코드에서 정함) 첫 화면(파란 배경) 입력칸 — 2026-11-03 사용자 첨부 로그인 시안: 반투명 흰 알약 + 왼쪽 선 아이콘 + 흰 글자.
// 라벨은 눈에 보이지 않고(.sr-only) placeholder 와 같은 문구로 보조기기가 읽는다. 그 밖의 props 는 <input> 에 그대로 간다.
// 같은 날 추가: tone="light" = 흰 배경용(연회색 알약 + 회색 아이콘·안내 글, 글자는 기본색).
// 쓰는 곳: 고객 앱 첫 화면(SignUp — light, 보관본 SignUpBlue — 기본)의 전화번호·비밀번호 칸.
import { useId } from 'react';
import AuthPhoneIcon from '../../icons/AuthPhoneIcon.jsx';
import AuthLockIcon from '../../icons/AuthLockIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './AuthPillField.module.css';

const ICONS = { phone: <AuthPhoneIcon />, lock: <AuthLockIcon /> };

/**
 * @param {object} props
 * @param {'phone'|'lock'} props.icon 왼쪽 아이콘
 * @param {string} props.label 보조기기용 이름 (화면에는 placeholder 로 보인다)
 * @param {string} [props.placeholder] 생략하면 label
 * @param {string} [props.value]
 * @param {(e: import('react').ChangeEvent<HTMLInputElement>) => void} [props.onChange]
 * @param {'onBlue'|'light'} [props.tone='onBlue'] onBlue = 파란 배경 위 반투명 흰 알약, light = 흰 배경 위 연회색 알약
 * @param {string} [props.className]
 */
export default function AuthPillField({ icon, label, placeholder, value, onChange, tone = 'onBlue', className, ...inputProps }) {
  const id = `pill-${useId()}`;
  return (
    <div className={cx(styles.field, tone === 'light' && styles.light, className)}>
      <label htmlFor={id} className="sr-only">{label}</label>
      <span className={styles.icon}>{ICONS[icon]}</span>
      <input id={id} className={cx('text-body-m', styles.input)} placeholder={placeholder ?? label} value={value} onChange={onChange} {...inputProps} />
    </div>
  );
}
