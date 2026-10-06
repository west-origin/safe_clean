// Figma: Live/LabeledControl › Exit (2164:2248). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 내려놓은 전화 수화기. 쓰는 곳(예): LiveLabeledControl 의 type="exit"("종료하기", 하단바형). 알약형은 LiveExitIcon.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LiveHangUpIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M4.80002 9.2C8.80002 6.6 15.2 6.6 19.2 9.2C20.2 9.9 20.5 11 20.1 11.9L19.2 13.7C18.8 14.5 17.9 14.8 17.1 14.5L14.7 13.6C14 13.3 13.6 12.6 13.7 11.9V11C11.7 10.4 9.50002 10.4 7.50002 11V11.9C7.60002 12.6 7.20002 13.3 6.50002 13.6L4.10002 14.5C3.30002 14.8 2.40002 14.5 2.00002 13.7L1.10002 11.9C0.700017 11 1.00002 9.9 2.00002 9.2H4.80002Z" fill="currentColor" />
    </svg>
  );
}
