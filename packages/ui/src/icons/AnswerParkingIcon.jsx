// (코드에서 정함) 주의사항 선택 아이콘 — 주차 표지판 (무료주차 가능 / prohibited = 불가능). 2026-11-03 사용자 요청으로 홈 서비스 아이콘과 같은 형식으로 다시 그렸다.
// 첨부 시안(홈의 매장·가정·원룸 아이콘) 패턴: 테두리 없는 플랫 면 + 그 팔레트(tokens/extra.css 의 --raw-home-icon-*) + 흰 디테일, 화면에서 연회색 원 위에 놓는다.
// prohibited 면 같은 그림 위에 빨간 금지 표시(원 + 사선). 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {boolean} [props.prohibited] 금지 표시를 덧그린다 ('불가능')
 * @param {string} [props.className]
 */
export default function AnswerParkingIcon({ size = 48, prohibited = false, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="21.5" y="28" width="5" height="15" rx="1" fill="var(--raw-home-icon-door)" />
      <rect x="10" y="5" width="28" height="26" rx="5" fill="var(--raw-home-icon-window)" />
      <path d="M20 25.5V11h5.5a4.75 4.75 0 0 1 0 9.5H20" stroke="var(--color-surface-default)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      {prohibited && (
        <g stroke="var(--raw-home-icon-roof)" strokeWidth="4" strokeLinecap="round">
          <circle cx="24" cy="24" r="20" />
          <path d="M10 38 38 10" />
        </g>
      )}
    </svg>
  );
}
