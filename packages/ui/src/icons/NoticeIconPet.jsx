// Figma: NoticeIcon/Pet (2093:2064). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 발바닥. NoticeChip 에 icon="pet" 으로 고른다 (noticeIcons.js 표). 고객 앱 api/mock.js 예: "반려동물이 있어요" 칩.

/**
 * @param {object} props
 * @param {number} [props.size=16] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function NoticeIconPet({ size = 16, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4 6.5C4.82843 6.5 5.5 5.82843 5.5 5C5.5 4.17157 4.82843 3.5 4 3.5C3.17157 3.5 2.5 4.17157 2.5 5C2.5 5.82843 3.17157 6.5 4 6.5Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8 5.1001C8.82843 5.1001 9.5 4.42852 9.5 3.6001C9.5 2.77167 8.82843 2.1001 8 2.1001C7.17157 2.1001 6.5 2.77167 6.5 3.6001C6.5 4.42852 7.17157 5.1001 8 5.1001Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12 6.5C12.8284 6.5 13.5 5.82843 13.5 5C13.5 4.17157 12.8284 3.5 12 3.5C11.1716 3.5 10.5 4.17157 10.5 5C10.5 5.82843 11.1716 6.5 12 6.5Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M7.9999 8C10.1999 8 11.5999 9.6 11.5999 11.1C11.5999 12.6 10.1999 13.5 7.9999 13.5C5.7999 13.5 4.3999 12.7 4.3999 11.1C4.3999 9.5 5.7999 8 7.9999 8Z" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
