// (코드에서 정함) 주의사항 선택 아이콘 — 물음표 (무료주차 모름). 2026-11-03 사용자 요청으로 홈 서비스 아이콘과 같은 형식으로 다시 그렸다.
// 첨부 시안(홈의 매장·가정·원룸 아이콘) 패턴: 테두리 없는 플랫 면 + 그 팔레트(tokens/extra.css 의 --raw-home-icon-*) + 흰 디테일, 화면에서 연회색 원 위에 놓는다.
// 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AnswerUnknownIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="24" cy="24" r="17" fill="var(--raw-home-icon-door)" />
      <path d="M18.5 19a5.5 5.5 0 1 1 7.8 5c-1.4.7-2.3 1.9-2.3 3.4v1.1" stroke="var(--color-surface-default)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="24" cy="34.5" r="2.4" fill="var(--color-surface-default)" />
    </svg>
  );
}
