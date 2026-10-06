// Figma: Auth/TermsConsent › Checkbox › Vector (2075:1861). 원형 체크박스 안의 체크 표시(25 캔버스 기준, stroke 2.2).
// path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 쓰는 곳(예): Checkbox (원형 체크박스 안 체크 표시).

/**
 * @param {object} props
 * @param {number} [props.size=25] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function CheckMarkIcon({ size = 25, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 25 25" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M6.7998 12.8998L10.6998 16.5998L18.1998 8.7998" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
