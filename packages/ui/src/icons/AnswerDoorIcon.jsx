// (코드에서 정함) 주의사항 선택 아이콘 — 문 (공동현관 비밀번호 있음 / prohibited = 없음). 2026-11-03 사용자 요청.
// 첨부 시안(홈의 매장·가정·원룸 아이콘) 패턴: 테두리 없는 플랫 면 + 그 팔레트(tokens/extra.css 의 --raw-home-icon-*) + 흰 디테일, 화면에서 연회색 원 위에 놓는다.
// 모양: 회색 문틀 + 진회색 문짝 + 하늘색 작은 창 + 흰 손잡이. prohibited 면 빨간 금지 표시(원 + 사선)를 덧그린다.
// 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {boolean} [props.prohibited] 금지 표시를 덧그린다 ('없음')
 * @param {string} [props.className]
 */
export default function AnswerDoorIcon({ size = 48, prohibited = false, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="11" y="5" width="26" height="38" rx="3" fill="var(--raw-home-icon-wall)" />
      <rect x="15" y="9" width="18" height="34" rx="1.5" fill="var(--raw-home-icon-door)" />
      <rect x="18.5" y="13" width="11" height="7" rx="1.5" fill="var(--raw-home-icon-window)" />
      <circle cx="28.5" cy="28" r="2" fill="var(--color-surface-default)" />
      {prohibited && (
        <g stroke="var(--raw-home-icon-roof)" strokeWidth="4" strokeLinecap="round">
          <circle cx="24" cy="24" r="20" />
          <path d="M10 38 38 10" />
        </g>
      )}
    </svg>
  );
}
