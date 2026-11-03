// (코드에서 정함) 완료 표시 — 가는 선 원 + 체크. 2026-11-03 사용자 첨부 '예약 완료' 시안. 색은 currentColor (화면이 status/success 초록을 준다).
// 쓰는 곳: 고객 앱 BookingComplete 위쪽.

/**
 * @param {object} props
 * @param {number} [props.size=56] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function SuccessCheckIcon({ size = 56, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 56 56" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="28" cy="28" r="25" stroke="currentColor" strokeWidth="2.5" />
      <path d="m17.5 28.5 7 7 14-14.5" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
