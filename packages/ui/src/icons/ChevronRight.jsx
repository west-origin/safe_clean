// Figma: Button/Primary › Chevron (2029:553). 벡터 7×14, stroke 2.5 (내보낸 SVG 는 stroke 여백 포함 9.5×16.5).
// path 데이터는 Figma 에서 내보낸 원본 그대로. 색은 currentColor (부모의 color 를 따른다).
// 모양: 오른쪽 꺾쇠. 쓰는 곳(예): Button — chevron 을 보이는 버튼의 오른쪽 끝 (Loading·Disabled 에서는 숨긴다).

/**
 * @param {object} props
 * @param {number} [props.size=16.5] 높이(px). 너비는 비율대로.
 * @param {string} [props.className]
 */
export default function ChevronRight({ size = 16.5, className }) {
  return (
    <svg
      width={size * (9.5 / 16.5)}
      height={size}
      viewBox="0 0 9.5 16.5"
      fill="none"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path
        d="M1.25 1.25L8.25 8.25L1.25 15.25"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
