// Figma: Manager/ChecklistItem › Check (2114:2232). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 체크 표시. 쓰는 곳(예): ChecklistItem(체크됐을 때만), SuccessHeader(size 22).

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function CheckIcon({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3.33333 8.33333L6.33333 11.3333L13.3333 4.33333" stroke="currentColor" strokeWidth={1.33333} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
