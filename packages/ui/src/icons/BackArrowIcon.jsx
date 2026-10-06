// Figma: Auth/TermsConsent › Button/Back (2075:1809). 고객용 뒤로가기 화살표(24, stroke 2.4). 매니저 NavBar 의 BackIcon(stroke 2)과 모양이 달라 분리.
// path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 왼쪽 꺾쇠. 쓰는 곳(예): BackButton, StepNavBar (고객 앱의 뒤로가기 버튼 안).

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BackArrowIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M15 4.5L7.5 12L15 19.5" stroke="currentColor" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
