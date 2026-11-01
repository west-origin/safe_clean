// (코드에서 정함) 주의사항 선택 아이콘 — 새·기타 반려동물. 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 선 아이콘. 색은 currentColor.
// 쓰는 곳: 고객 앱 BookingNotesRequests (CircleChoice).

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AnswerBirdIcon({ size = 40, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="20" cy="22" r="11" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M20 11c-1-2-.5-4 1.5-5M20 11c1.5-1.5 3.5-2 5-1" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="16" cy="20" r="1.3" fill="currentColor" />
      <circle cx="24" cy="20" r="1.3" fill="currentColor" />
      <path d="M17.5 24h5L20 27.5Z" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
