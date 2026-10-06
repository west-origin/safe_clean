// Figma: Manager/ListItem › Chevron (2114:2241). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 오른쪽 꺾쇠. 쓰는 곳(예): ListItem 오른쪽 끝 (매니저 앱 목록 줄).

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ChevronRightSmallIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M6.75 4.5L11.25 9L6.75 13.5" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
