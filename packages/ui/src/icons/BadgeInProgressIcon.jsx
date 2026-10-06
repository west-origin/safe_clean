// Figma: Manager/StatusBadge › BadgeIcon/InProgress (2110:2216). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 오른쪽을 향한 삼각형(재생). 쓰는 곳(예): StatusBadge 의 status="inProgress"(진행 중) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgeInProgressIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4.66667 2.91667V11.0833L11.0833 7L4.66667 2.91667Z" fill="currentColor" />
    </svg>
  );
}
