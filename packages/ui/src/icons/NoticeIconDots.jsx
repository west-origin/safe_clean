// Figma: NoticeIcon/Dots (2093:2097). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 점 세 개. NoticeChip 에 icon="dots" 로 고른다 — icon 을 안 주면 NoticeChip 의 기본값이다. 고객 앱 api/mock.js 예: "기타" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconDots({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3.6 9.0999C4.20751 9.0999 4.7 8.60742 4.7 7.9999C4.7 7.39239 4.20751 6.8999 3.6 6.8999C2.99249 6.8999 2.5 7.39239 2.5 7.9999C2.5 8.60742 2.99249 9.0999 3.6 9.0999Z" fill="currentColor" />
      <path d="M7.9999 9.0999C8.60742 9.0999 9.0999 8.60742 9.0999 7.9999C9.0999 7.39239 8.60742 6.8999 7.9999 6.8999C7.39239 6.8999 6.8999 7.39239 6.8999 7.9999C6.8999 8.60742 7.39239 9.0999 7.9999 9.0999Z" fill="currentColor" />
      <path d="M12.4 9.0999C13.0076 9.0999 13.5 8.60742 13.5 7.9999C13.5 7.39239 13.0076 6.8999 12.4 6.8999C11.7925 6.8999 11.3 7.39239 11.3 7.9999C11.3 8.60742 11.7925 9.0999 12.4 9.0999Z" fill="currentColor" />
    </svg>
  );
}
