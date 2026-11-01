// 2026-10-31 사용자 첨부 주소 검색 시안의 X(닫기) — Figma 에 없음. 색은 currentColor.
// 모양: 가는 X. 쓰는 곳(예): NavBar icon="close".

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function CloseIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M5 5L19 19M19 5L5 19" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" />
    </svg>
  );
}
