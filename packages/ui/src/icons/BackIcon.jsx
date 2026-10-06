// Figma: Manager/NavBar › Button/Back (2121:2265). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 왼쪽 꺾쇠. 쓰는 곳(예): NavBar(매니저 앱 상단 바)의 뒤로가기 버튼. 고객 앱은 BackArrowIcon 을 쓴다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BackIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M15 6L9 12L15 18" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
