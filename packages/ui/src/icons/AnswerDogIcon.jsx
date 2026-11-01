// (코드에서 정함) 주의사항 선택 아이콘 — 강아지. 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 선 아이콘. 색은 currentColor.
// 쓰는 곳: 고객 앱 BookingNotesRequests (CircleChoice).

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AnswerDogIcon({ size = 40, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M12 12c-3-1-6 1-6 5 0 3 1.5 6 3.5 6.5M28 12c3-1 6 1 6 5 0 3-1.5 6-3.5 6.5" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M11 14c2-3 5.5-4.5 9-4.5s7 1.5 9 4.5c1 2 1.5 5 1.5 8 0 6-4.5 10.5-10.5 10.5S9.5 28 9.5 22c0-3 .5-6 1.5-8Z" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="15.5" cy="20" r="1.3" fill="currentColor" />
      <circle cx="24.5" cy="20" r="1.3" fill="currentColor" />
      <path d="M18 25h4l-2 2Z" fill="currentColor" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M20 27v2.5" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
