// (코드에서 정함) 주의사항 선택 아이콘 — 아기 얼굴 (영유아 있음 / outline = 없음). 강아지 아이콘(AnswerDogIcon)과 같은 형식 (2026-11-03 사용자 요청):
// 매장 아이콘처럼 플랫 컬러 칠 + 검정 선(currentColor), outline 이면 칠을 빼고 선만. 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=56] 한 변 크기(px)
 * @param {boolean} [props.outline] 칠 없이 검정 선만 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerBabyIcon({ size = 56, outline = false, className }) {
  const fill = (color) => (outline ? 'none' : `var(${color})`);
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <g stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="9.5" cy="26" r="3" fill={fill('--raw-home-icon-box-left')} />
        <circle cx="38.5" cy="26" r="3" fill={fill('--raw-home-icon-box-left')} />
        <circle cx="24" cy="26" r="14" fill={fill('--raw-home-icon-box-top')} />
        <path d="M22 12.5c-.5-2.5 1-4.5 3.5-4.5 1.8 0 2.8 1.4 2.3 2.8" />
        <path d="M20.5 31c1.6 2 5.4 2 7 0" />
      </g>
      <circle cx="18.5" cy="25" r="1.7" fill="currentColor" />
      <circle cx="29.5" cy="25" r="1.7" fill="currentColor" />
      {!outline && <circle cx="15" cy="29.5" r="2.2" fill="var(--raw-home-icon-roof)" opacity={0.35} />}
      {!outline && <circle cx="33" cy="29.5" r="2.2" fill="var(--raw-home-icon-roof)" opacity={0.35} />}
    </svg>
  );
}
