// Figma: Manager/StatusBadge › BadgeIcon/Paused (2110:2224). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 세로 막대 두 개(일시정지). 쓰는 곳(예): StatusBadge 의 status="paused"(일시정지) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgePausedIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4.66667 2.91667H6.41667V11.0833H4.66667V2.91667ZM7.58333 2.91667H9.33333V11.0833H7.58333V2.91667Z" fill="currentColor" />
    </svg>
  );
}
