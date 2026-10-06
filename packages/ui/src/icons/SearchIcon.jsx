// Figma: Auth/ProfileAddress › Field/RoadAddress › Icon/Search (2075:1847). 30×30, stroke 2. path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 돋보기. 쓰는 곳(예): SearchField 입력칸 왼쪽 (색은 SearchField.module.css 가 정한다).

/**
 * @param {object} props
 * @param {number} [props.size=30] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function SearchIcon({ size = 30, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 30 30" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path
        d="M12.9998 21.1998C17.5285 21.1998 21.1998 17.5285 21.1998 12.9998C21.1998 8.47107 17.5285 4.7998 12.9998 4.7998C8.47107 4.7998 4.7998 8.47107 4.7998 12.9998C4.7998 17.5285 8.47107 21.1998 12.9998 21.1998Z"
        stroke="currentColor"
        strokeWidth={2}
      />
      <path d="M19.2002 19.2002L25.5002 25.5002" stroke="currentColor" strokeWidth={2} strokeLinecap="round" />
    </svg>
  );
}
