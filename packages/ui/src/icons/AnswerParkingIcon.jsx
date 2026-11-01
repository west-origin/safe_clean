// (코드에서 정함) 주의사항 선택 아이콘 — 무료주차 가능. 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 선 아이콘. 색은 currentColor.
// 쓰는 곳: 고객 앱 BookingNotesRequests (CircleChoice).

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AnswerParkingIcon({ size = 40, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="8" y="8" width="24" height="24" rx="5" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M16 28V13h5a4.5 4.5 0 0 1 0 9h-5" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
