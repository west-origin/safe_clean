// Figma: Settings › ProfileCard/MenuRow › Chevron (2048:1728). 7×12, stroke 1.6. path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 작은 오른쪽 꺾쇠. 쓰는 곳(예): MenuRow, SelectField(기본 아이콘). FaqItem 은 펼칠 때 CSS 로 90도 돌려 쓴다.

/**
 * @param {object} props
 * @param {number} [props.size=12] 세로 크기(px). 가로는 비율(7/12)
 * @param {string} [props.className]
 */
export default function ChevronRightTinyIcon({ size = 12, className }) {
  return (
    <svg width={(size * 7) / 12} height={size} viewBox="0 0 7 12" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M0.800049 0.799805L5.80005 5.7998L0.800049 10.7998" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
