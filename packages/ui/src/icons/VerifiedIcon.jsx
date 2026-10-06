// Figma: Booking/AddressConfirm › Icon/Verified (2075:1880). 25 원(text/primary) + 흰 체크. 원은 currentColor, 체크는 surface/default.
// 쓰는 곳(예): 고객 앱 BookingAddressConfirm(예약 주소 확인) 화면. 체크 선은 CSS 변수로 고정이라 currentColor 로는 원 색만 바뀐다.

/**
 * @param {object} props
 * @param {number} [props.size=25]
 * @param {string} [props.className]
 */
export default function VerifiedIcon({ size = 25, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 25 25" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M12.5 25C19.4036 25 25 19.4036 25 12.5C25 5.59644 19.4036 0 12.5 0C5.59644 0 0 5.59644 0 12.5C0 19.4036 5.59644 25 12.5 25Z" fill="currentColor" />
      <path d="M6.7998 12.8998L10.6998 16.5998L18.1998 8.7998" stroke="var(--color-surface-default)" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
