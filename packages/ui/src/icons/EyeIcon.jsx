// Figma: Auth/LoginPhone › Input/Number › Icon/TogglePassword (2075:1856). 20×19, stroke 1.6.
// path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 눈. 쓰는 곳(예): PhoneField 의 "번호 가리기" 토글 버튼 (입력한 번호를 가리고 보이기).

/**
 * @param {object} props
 * @param {number} [props.size=20] 가로 크기(px). 세로는 비율(19/20)로 따라간다
 * @param {string} [props.className]
 */
export default function EyeIcon({ size = 20, className }) {
  return (
    <svg width={size} height={(size * 19) / 20} viewBox="0 0 20 19" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path
        d="M1.2002 9.5001C3.7002 5.2001 6.7002 3.1001 10.0002 3.1001C13.3002 3.1001 16.3002 5.2001 18.8002 9.5001C16.3002 13.8001 13.3002 15.9001 10.0002 15.9001C6.7002 15.9001 3.7002 13.8001 1.2002 9.5001Z"
        stroke="currentColor"
        strokeWidth={1.6}
        strokeLinejoin="round"
      />
      <path d="M10 12.5C11.6569 12.5 13 11.1569 13 9.5C13 7.84315 11.6569 6.5 10 6.5C8.34315 6.5 7 7.84315 7 9.5C7 11.1569 8.34315 12.5 10 12.5Z" stroke="currentColor" strokeWidth={1.6} />
    </svg>
  );
}
