// Figma: Manager/StatusBadge › BadgeIcon/SettlementPending (2110:2232). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 시계(원 + 바늘). 쓰는 곳(예): StatusBadge 의 status="settlementPending"(정산 예정) 라벨 앞 (StatusBadge/status.js 표).

/**
 * @param {object} props
 * @param {number} [props.size=14] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BadgeSettlementPendingIcon({ size = 14, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M7 4.08333V7L9.33333 8.16667M7 12.25C7.68944 12.25 8.37213 12.1142 9.00909 11.8504C9.64605 11.5865 10.2248 11.1998 10.7123 10.7123C11.1998 10.2248 11.5865 9.64605 11.8504 9.00909C12.1142 8.37213 12.25 7.68944 12.25 7C12.25 6.31056 12.1142 5.62787 11.8504 4.99091C11.5865 4.35395 11.1998 3.7752 10.7123 3.28769C10.2248 2.80018 9.64605 2.41347 9.00909 2.14963C8.37213 1.8858 7.68944 1.75 7 1.75C5.60761 1.75 4.27226 2.30312 3.28769 3.28769C2.30312 4.27226 1.75 5.60761 1.75 7C1.75 8.39239 2.30312 9.72774 3.28769 10.7123C4.27226 11.6969 5.60761 12.25 7 12.25Z" stroke="currentColor" strokeWidth={1.16667} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
