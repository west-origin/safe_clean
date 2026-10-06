// TODO(figma): InfoIcon/Won 은 Figma 에 없다 — 예약 완료 카드의 "결제 예정 금액" 행(BACKEND_PLAN.md §9.2-7)을 위해 코드에서 그림. 다른 InfoIcon 과 같은 20 viewBox·1.6 stroke
// 모양: 원 안 원화 기호(W 와 가로줄 두 개). 쓰는 곳(예): BookingInfoCard 의 icon="won" 행.
// StackedInfoCard 의 아이콘 표에는 won 이 없어서, 거기에 icon="won" 을 넘기면 렌더 중 오류가 난다.

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function InfoWonIcon({ size = 20, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 20 20" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M10 17C13.866 17 17 13.866 17 10C17 6.13401 13.866 3 10 3C6.13401 3 3 6.13401 3 10C3 13.866 6.13401 17 10 17Z" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6.2 7L7.8 13L10 8.2L12.2 13L13.8 7M5.6 9.6H14.4M5.6 11.2H14.4" stroke="currentColor" strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
