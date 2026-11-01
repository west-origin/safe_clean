// (코드에서 정함) 주의사항 선택 아이콘 — 아기 (dashed = 영유아 없음). 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 선 아이콘. 색은 currentColor.
// 쓰는 곳: 고객 앱 BookingNotesRequests (CircleChoice).

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {boolean} [props.dashed] 점선으로 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerBabyIcon({ size = 40, dashed = false, className }) {
  const dash = dashed ? '2 2' : undefined; // 점선 간격
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="20" cy="21" r="12" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={dash} />
      {!dashed && <path d="M18 9.5c0 2 1 3 2.5 3M15.5 20v1M24.5 20v1M17.5 26a3 3 0 0 0 5 0" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />}
      {!dashed && <circle cx="20" cy="27.5" r="1.6" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />}
    </svg>
  );
}
