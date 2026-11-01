// 2026-10-31 서비스 시간 시트 시안의 − (DurationStepper) — Figma 에 없음. 색은 currentColor.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function MinusIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M5 12H19" stroke="currentColor" strokeWidth={2} strokeLinecap="round" />
    </svg>
  );
}
