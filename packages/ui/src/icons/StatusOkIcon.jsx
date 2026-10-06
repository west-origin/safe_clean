// Figma: Manager/CameraStatusItem › StatusIcon/Normal (2147:2996). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 체크 표시. 쓰는 곳(예): CameraStatusItem 의 status="ok"(정상). 문제가 있으면 StatusIssueIcon.

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function StatusOkIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3.75 9.375L7.125 12.75L15 4.875" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
