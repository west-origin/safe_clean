// Figma: Manager/Home/Empty › Icon/Empty (2146:3005). 36px, stroke 2.7. 색은 currentColor.
// 모양: 웃는 얼굴. 쓰는 곳(예): EmptyState 에 icon="smile" 을 줄 때, 그리고 모르는 icon 이름일 때의 기본값.

/**
 * @param {object} props
 * @param {number} [props.size=36]
 * @param {string} [props.className]
 */
export default function EmptySmileIcon({ size = 36, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M12.75 20.25C12.75 20.25 14.7 22.95 18 22.95C21.3 22.95 23.25 20.25 23.25 20.25M13.5 14.25H13.515M22.5 14.25H22.515M18 31.5C19.7728 31.5 21.5283 31.1508 23.1662 30.4724C24.8041 29.7939 26.2923 28.7995 27.5459 27.5459C28.7995 26.2923 29.7939 24.8041 30.4724 23.1662C31.1508 21.5283 31.5 19.7728 31.5 18C31.5 16.2272 31.1508 14.4717 30.4724 12.8338C29.7939 11.1959 28.7995 9.70765 27.5459 8.45406C26.2923 7.20047 24.8041 6.20606 23.1662 5.52763C21.5283 4.84919 19.7728 4.5 18 4.5C14.4196 4.5 10.9858 5.92232 8.45406 8.45406C5.92232 10.9858 4.5 14.4196 4.5 18C4.5 21.5804 5.92232 25.0142 8.45406 27.5459C10.9858 30.0777 14.4196 31.5 18 31.5Z" stroke="currentColor" strokeWidth="2.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
