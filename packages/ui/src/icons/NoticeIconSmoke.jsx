// Figma: NoticeIcon/Smoke (2093:2073). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 담배와 연기. NoticeChip 에 icon="smoke" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "흡연 공간이 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconSmoke({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M2 11.3999H11M12.6 11.3999H14" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.5 3.2002C11.5 4.1002 11.5 5.1002 10.5 6.0002C9.5 6.9002 9.5 7.9002 10.5 8.8002" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
