// Figma: Manager/StatusBadge › BadgeIcon/Scheduled (2110:2212). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 달력. 쓰는 곳(예): StatusBadge 의 status="scheduled"(방문 예정) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgeScheduledIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M1.75 4.66667H12.25M4.08333 1.75V4.08333M9.91667 1.75V4.08333M2.91667 2.91667H11.0833C11.3928 2.91667 11.6895 3.03958 11.9083 3.25838C12.1271 3.47717 12.25 3.77391 12.25 4.08333V11.0833C12.25 11.3928 12.1271 11.6895 11.9083 11.9083C11.6895 12.1271 11.3928 12.25 11.0833 12.25H2.91667C2.60725 12.25 2.3105 12.1271 2.09171 11.9083C1.87292 11.6895 1.75 11.3928 1.75 11.0833V4.08333C1.75 3.77391 1.87292 3.47717 2.09171 3.25838C2.3105 3.03958 2.60725 2.91667 2.91667 2.91667Z" stroke="currentColor" strokeWidth={1.16667} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
