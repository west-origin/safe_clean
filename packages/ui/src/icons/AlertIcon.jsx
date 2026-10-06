// Figma: Manager/Job/Preparing/Error › Icon/Alert (2146:2857). 28px, stroke 2.333. 색은 currentColor.
// 모양: 삼각형 안 느낌표(경고). 쓰는 곳(예): ErrorState(size 36), 매니저 앱 JobPreparing 의 "카메라 연결 실패" 카드.

/**
 * @param {object} props
 * @param {number} [props.size=28]
 * @param {string} [props.className]
 */
export default function AlertIcon({ size = 28, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M14 10.5V15.75M14 20.0667H14.0117M12.0167 4.55L2.1 21C1.99575 21.1803 1.94166 21.3852 1.94334 21.5934C1.94502 21.8017 2.00241 22.0057 2.10956 22.1843C2.2167 22.3628 2.3697 22.5095 2.55266 22.609C2.73562 22.7085 2.94187 22.7571 3.15 22.75H24.85C25.0581 22.7571 25.2644 22.7085 25.4473 22.609C25.6303 22.5095 25.7833 22.3628 25.8904 22.1843C25.9976 22.0057 26.055 21.8017 26.0567 21.5934C26.0583 21.3852 26.0043 21.1803 25.9 21L15.9833 4.55C15.8787 4.38117 15.7327 4.24185 15.5592 4.14524C15.3856 4.04862 15.1903 3.99791 14.9917 3.99791C14.793 3.99791 14.5977 4.04862 14.4242 4.14524C14.2506 4.24185 14.1046 4.38117 14 4.55H12.0167Z" stroke="currentColor" strokeWidth="2.33333" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
