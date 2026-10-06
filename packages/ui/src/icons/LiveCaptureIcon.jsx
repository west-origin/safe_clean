// Figma: Live/PillControl › Icon/Capture (2160:2220). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 카메라. 쓰는 곳(예): LivePillControl 의 type="capture"("화면 캡처", 영상 위 알약 버튼). 하단바형은 LiveCaptureLargeIcon.

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LiveCaptureIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M3 5.625H5.55L6.525 4.125H11.475L12.45 5.625H15C15.1989 5.625 15.3897 5.70402 15.5303 5.84467C15.671 5.98532 15.75 6.17609 15.75 6.375V13.125C15.75 13.3239 15.671 13.5147 15.5303 13.6553C15.3897 13.796 15.1989 13.875 15 13.875H3C2.80109 13.875 2.61032 13.796 2.46967 13.6553C2.32902 13.5147 2.25 13.3239 2.25 13.125V6.375C2.25 6.17609 2.32902 5.98532 2.46967 5.84467C2.61032 5.70402 2.80109 5.625 3 5.625ZM9 12C9.6763 12 10.3249 11.7313 10.8031 11.2531C11.2813 10.7749 11.55 10.1263 11.55 9.45C11.55 8.7737 11.2813 8.1251 10.8031 7.64688C10.3249 7.16866 9.6763 6.9 9 6.9C8.3237 6.9 7.6751 7.16866 7.19688 7.64688C6.71866 8.1251 6.45 8.7737 6.45 9.45C6.45 10.1263 6.71866 10.7749 7.19688 11.2531C7.6751 11.7313 8.3237 12 9 12Z" stroke="currentColor" strokeWidth="1.425" strokeLinejoin="round" />
    </svg>
  );
}
