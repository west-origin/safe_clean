// Figma: Manager/Job/Complete › Icon/CompleteCheck (2145:2854). 56px, 초록 원(currentColor) + 흰 체크(text/inverse).
// 쓰는 곳(예): 매니저 앱 JobComplete(작업 완료) 화면 위쪽. 체크 선은 currentColor 가 아니라 CSS 변수로 고정이라 원 색만 바뀐다.

/**
 * @param {object} props
 * @param {number} [props.size=56]
 * @param {string} [props.className]
 */
export default function CompleteCheckIcon({ size = 56, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 56 56" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M28 51.3333C40.8866 51.3333 51.3333 40.8866 51.3333 28C51.3333 15.1134 40.8866 4.66667 28 4.66667C15.1134 4.66667 4.66667 15.1134 4.66667 28C4.66667 40.8866 15.1134 51.3333 28 51.3333Z" fill="currentColor" />
      <path d="M17.5 29.1667L24.5 36.1667L38.5 21" stroke="var(--color-text-inverse)" strokeWidth="5.13333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
