// Figma: NoticeIcon/Droplet (2093:2112). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 물방울. NoticeChip 에 icon="droplet" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "물 사용이 어려워요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconDroplet({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 2.3999C8 2.3999 12 6.4999 12 8.8999C12 9.96077 11.5786 10.9782 10.8284 11.7283C10.0783 12.4785 9.06087 12.8999 8 12.8999C6.93913 12.8999 5.92172 12.4785 5.17157 11.7283C4.42143 10.9782 4 9.96077 4 8.8999C4 6.4999 8 2.3999 8 2.3999Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
