// (코드에서 정함) 주의사항 선택 아이콘 — 물음표 (모름). 강아지 아이콘(AnswerDogIcon)과 같은 형식 (2026-11-03 사용자 요청):
// 매장 아이콘처럼 플랫 컬러 칠 + 검정 선(currentColor). 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup) 의 무료주차 '모름'.

/**
 * @param {object} props
 * @param {number} [props.size=56] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AnswerUnknownIcon({ size = 56, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <g stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="24" cy="24" r="15" fill="var(--raw-home-icon-wall)" />
        <path d="M19.5 19.5a4.5 4.5 0 1 1 6.4 4.1c-1.2.6-1.9 1.6-1.9 2.9v1" strokeWidth={2.6} />
      </g>
      <circle cx="24" cy="32" r="1.7" fill="currentColor" />
    </svg>
  );
}
