// Figma: NoticeIcon/Server (2093:2129). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 서버(가로 칸 두 개). NoticeChip 에 icon="server" 로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "서버실이 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconServer({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M12 3H4.00005C3.33731 3 2.80005 3.53726 2.80005 4.2V5.8C2.80005 6.46274 3.33731 7 4.00005 7H12C12.6628 7 13.2 6.46274 13.2 5.8V4.2C13.2 3.53726 12.6628 3 12 3Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12 9H4.00005C3.33731 9 2.80005 9.53726 2.80005 10.2V11.8C2.80005 12.4627 3.33731 13 4.00005 13H12C12.6628 13 13.2 12.4627 13.2 11.8V10.2C13.2 9.53726 12.6628 9 12 9Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M5.19995 5H5.20995M5.19995 11H5.20995" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
