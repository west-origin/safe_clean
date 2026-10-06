// Figma: Live/LabeledControl › Capture (2164:2243). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 카메라. 쓰는 곳(예): LiveLabeledControl 의 type="capture"("화면 캡처", 하단바형). 영상 위 알약형은 LiveCaptureIcon.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LiveCaptureLargeIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4 7.5H7.4L8.7 5.5H15.3L16.6 7.5H20C20.2652 7.5 20.5196 7.60536 20.7071 7.79289C20.8946 7.98043 21 8.23478 21 8.5V17.5C21 17.7652 20.8946 18.0196 20.7071 18.2071C20.5196 18.3946 20.2652 18.5 20 18.5H4C3.73478 18.5 3.48043 18.3946 3.29289 18.2071C3.10536 18.0196 3 17.7652 3 17.5V8.5C3 8.23478 3.10536 7.98043 3.29289 7.79289C3.48043 7.60536 3.73478 7.5 4 7.5ZM12 16C12.9017 16 13.7665 15.6418 14.4042 15.0042C15.0418 14.3665 15.4 13.5017 15.4 12.6C15.4 11.6983 15.0418 10.8335 14.4042 10.1958C13.7665 9.55821 12.9017 9.2 12 9.2C11.0983 9.2 10.2335 9.55821 9.59584 10.1958C8.95821 10.8335 8.6 11.6983 8.6 12.6C8.6 13.5017 8.95821 14.3665 9.59584 15.0042C10.2335 15.6418 11.0983 16 12 16Z" stroke="currentColor" strokeWidth="1.9" strokeLinejoin="round" />
    </svg>
  );
}
