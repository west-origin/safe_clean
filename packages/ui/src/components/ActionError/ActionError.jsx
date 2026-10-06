// 버튼 동작(저장·취소·수락 …)이 실패했을 때의 한 줄 문구. useAction 의 error 를 그대로 받는다 (CLAUDE.md §10).
// 화면마다 <p role="alert"> 를 손으로 복사하던 것(34곳)을 한 곳으로 (검증 검토).
import { cx } from '../../utils/cx.js';
import styles from './ActionError.module.css';

// 실패 문구를 <p role="alert"> 로 그린다. role="alert" 는 문구가 나타나는 순간 스크린리더가 바로 읽어 주게 한다.
// 두 앱의 여러 화면이 `<ActionError error={save.error} />` 처럼 useAction 의 error 를 넘긴다 ({ message } 객체를 직접 넘기는 곳도 있다).
/**
 * @param {object} props
 * @param {{ message?: string }|null|undefined} props.error useAction 의 error — 없으면 아무것도 그리지 않는다
 * @param {string} [props.fallback='잠시 후 다시 시도해주세요'] error.message 가 비었을 때
 * @param {string} [props.message] 서버 문구 대신 항상 이 문구 (화면이 상황을 더 잘 아는 경우 — 예: 영상은 막혔는데 상태 변경만 실패)
 * @param {string} [props.className]
 * @param {import('react').ReactNode} [props.children] 문구 뒤에 붙는 것 (예: 로그인 링크)
 */
export default function ActionError({ error, fallback = '잠시 후 다시 시도해주세요', message, className, children }) {
  if (!error) return null;
  // 문구 우선순위: message prop → error.message(서버 문구 등) → fallback. (?? 는 null/undefined 일 때만, || 는 빈 문자열일 때도 다음 것으로)
  return (
    <p role="alert" className={cx('text-body-s', styles.error, className)}>
      {message ?? (error.message || fallback)}
      {children}
    </p>
  );
}
