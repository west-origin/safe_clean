// (코드에서 정함) 주의사항 선택 아이콘 — 주차 표지판 (무료주차 가능 / outline = 불가능). 강아지 아이콘(AnswerDogIcon)과 같은 형식 (2026-11-03 사용자 요청):
// 매장 아이콘처럼 플랫 컬러 칠 + 검정 선(currentColor), outline 이면 칠을 빼고 선만. 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=56] 한 변 크기(px)
 * @param {boolean} [props.outline] 칠 없이 검정 선만 ('불가능' 표시)
 * @param {string} [props.className]
 */
export default function AnswerParkingIcon({ size = 56, outline = false, className }) {
  const fill = (color) => (outline ? 'none' : `var(${color})`);
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <g stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <rect x="21.5" y="30" width="5" height="12" rx="1" fill={fill('--raw-home-icon-door')} />
        <rect x="10" y="6" width="28" height="25" rx="5" fill={fill('--raw-home-icon-window')} />
        <path d="M20 26V12h5a4.5 4.5 0 0 1 0 9h-5" strokeWidth={2.6} />
      </g>
    </svg>
  );
}
