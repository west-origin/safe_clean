// Figma: Manager/CallCard › Icon/Timer (2144:2810). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 시계. 쓰는 곳(예): CallCard 의 만료 칩(expiry, 예: "4:32 후 만료") 글자 앞.

/**
 * @param {object} props
 * @param {number} [props.size=12] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function TimerIcon({ size = 12, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 12 12" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M6 3.5V6L7.5 7M6 10.5C6.59095 10.5 7.17611 10.3836 7.72208 10.1575C8.26804 9.93131 8.76412 9.59984 9.18198 9.18198C9.59984 8.76412 9.93131 8.26804 10.1575 7.72208C10.3836 7.17611 10.5 6.59095 10.5 6C10.5 5.40905 10.3836 4.82389 10.1575 4.27792C9.93131 3.73196 9.59984 3.23588 9.18198 2.81802C8.76412 2.40016 8.26804 2.06869 7.72208 1.84254C7.17611 1.6164 6.59095 1.5 6 1.5C4.80653 1.5 3.66193 1.97411 2.81802 2.81802C1.97411 3.66193 1.5 4.80653 1.5 6C1.5 7.19347 1.97411 8.33807 2.81802 9.18198C3.66193 10.0259 4.80653 10.5 6 10.5Z" stroke="currentColor" strokeWidth={1.25} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
