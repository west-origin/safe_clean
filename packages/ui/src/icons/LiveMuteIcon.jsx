// Figma: Live/LabeledControl › Mute (2164:2253). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 스피커 + X. 쓰는 곳(예): LiveLabeledControl 의 type="mute"("음소거"). 같은 path 가 liveIcons.jsx 의 MuteSpeakerIcon 에도 있다.

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function LiveMuteIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M11 5.5L6.8 9H4C3.73478 9 3.48043 9.10536 3.29289 9.29289C3.10536 9.48043 3 9.73478 3 10V14C3 14.2652 3.10536 14.5196 3.29289 14.7071C3.48043 14.8946 3.73478 15 4 15H6.8L11 18.5V5.5Z" fill="currentColor" />
      <path d="M16.5 9.5L20.5 14.5M20.5 9.5L16.5 14.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}
