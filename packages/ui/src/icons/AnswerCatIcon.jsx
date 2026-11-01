// (코드에서 정함) 주의사항 선택 아이콘 — 고양이 (dashed = 반려동물 없음). 2026-11-01 사용자 첨부 '매니저님 방문 시 참고해야 할 정보' 시안의 선 아이콘. 색은 currentColor.
// 쓰는 곳: 고객 앱 BookingNotesRequests (CircleChoice).

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {boolean} [props.dashed] 점선으로 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerCatIcon({ size = 40, dashed = false, className }) {
  const dash = dashed ? '2 2' : undefined; // 점선 간격
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M9 11c-1 4-1.5 8-1.5 11.5C7.5 28.5 13 33 20 33s12.5-4.5 12.5-10.5c0-3.5-.5-7.5-1.5-11.5-3 1-6 3-7.5 4.5-1.2-.3-2.3-.5-3.5-.5s-2.3.2-3.5.5C15 14 12 12 9 11Z" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" strokeDasharray={dash} />
      {!dashed && <path d="M15 21.5v1M25 21.5v1M17.5 26c.8.8 1.6 1 2.5 0 .9 1 1.7.8 2.5 0" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" />}
    </svg>
  );
}
