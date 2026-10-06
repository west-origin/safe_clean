// Figma: Manager/StatusBadge › BadgeIcon/Complete (2110:2228). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 체크 표시. 쓰는 곳(예): StatusBadge 의 status="complete"(완료) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgeCompleteIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M2.91667 7.29167L5.54167 9.91667L11.6667 3.79167" stroke="currentColor" strokeWidth={1.16667} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
