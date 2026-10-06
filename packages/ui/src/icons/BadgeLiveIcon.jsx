// Figma: Manager/StatusBadge › BadgeIcon/Live (2110:2220). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 속이 찬 작은 원(점). 쓰는 곳(예): StatusBadge 의 status="live"(LIVE) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgeLiveIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3.5 7C3.5 7.92826 3.86875 8.8185 4.52513 9.47487C5.1815 10.1313 6.07174 10.5 7 10.5C7.92826 10.5 8.8185 10.1313 9.47487 9.47487C10.1313 8.8185 10.5 7.92826 10.5 7C10.5 6.07174 10.1313 5.1815 9.47487 4.52513C8.8185 3.86875 7.92826 3.5 7 3.5C6.07174 3.5 5.1815 3.86875 4.52513 4.52513C3.86875 5.1815 3.5 6.07174 3.5 7Z" fill="currentColor" />
    </svg>
  );
}
