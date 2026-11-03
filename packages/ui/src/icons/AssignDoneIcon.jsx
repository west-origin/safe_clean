// (코드에서 정함) 매니저 배정 완료 — 사람 + 체크. 2026-11-03 사용자 첨부 '배정 완료' 알약 시안. 색은 currentColor.
// 쓰는 곳: ReservationTicket 의 배정 알약.

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AssignDoneIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="8" cy="6.5" r="3.2" stroke="currentColor" strokeWidth="1.6" />
      <path d="M2.5 17c0-3.2 2.4-5 5.5-5 .9 0 1.7.1 2.4.4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      <path d="m12.2 14.6 1.9 1.9 3.6-3.8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
