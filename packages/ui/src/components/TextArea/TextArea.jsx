// Figma: Booking/NotesRequests › RequestCard › InputArea (2023:240) — 305×196, 1px border/default, radius 10, 패딩 상 16 / 좌 11, placeholder Caption/L text/secondary
// 여러 줄 입력칸. 쓰는 곳: 두 앱에서 긴 글을 받는 곳(예: 고객 앱 BookingNotesRequests 요청사항, 매니저 앱 Support 문의).
// 보이는 라벨이 없어 ariaLabel 이 필수다. 호출하는 화면은 maxLength 같은 나머지 속성도 넘기고,
// 그 속성은 그대로 <textarea> 에 붙는다.
import { useId } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './TextArea.module.css';

/**
 * @param {object} props
 * @param {string} props.ariaLabel 접근성 라벨 (Figma 에 별도 라벨 없음 — 카드 제목을 넘긴다)
 * @param {string} [props.placeholder]
 * @param {string} [props.value]
 * @param {(e: import('react').ChangeEvent<HTMLTextAreaElement>) => void} [props.onChange]
 * @param {boolean} [props.disabled]
 * @param {string} [props.id]
 * @param {string} [props.className]
 */
// 제어 컴포넌트: 값은 부모가 value 로 내려 주고, 입력하면 onChange(이벤트) 를 부른다. 서버 호출은 없다.
export default function TextArea({ ariaLabel, placeholder, value, onChange, disabled = false, id, className, ...rest }) {
  // useId: React 가 컴포넌트마다 겹치지 않는 id 를 만들어 준다. id 를 안 넘기면 이 값으로 만든다.
  const autoId = useId();
  return (
    <textarea id={id ?? `textarea-${autoId}`} aria-label={ariaLabel} className={cx('text-caption-l', styles.area, className)} placeholder={placeholder} value={value} onChange={onChange} disabled={disabled} {...rest} />
  );
}
