// (코드에서 정함) 주의사항 선택 아이콘 — 강아지 (반려동물 있음 / outline = 없음). 2026-11-03 사용자 첨부 시안(카드 안 강아지 얼굴)을
// 매장 아이콘(ServiceStoreIcon)과 같은 플랫 컬러로 그렸다. 선은 currentColor(카드 글자색 = 검정), 칠은 tokens/extra.css 의 --raw-home-icon-*.
// outline 이면 칠을 빼고 검정 선만 남긴다. 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=56] 한 변 크기(px)
 * @param {boolean} [props.outline] 칠 없이 검정 선만 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerDogIcon({ size = 56, outline = false, className }) {
  const fill = (color) => (outline ? 'none' : `var(${color})`);
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <g stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
        <path d="M10.5 22 11 8.5l9.5 6.5" fill={fill('--raw-home-icon-box-left')} />
        <path d="M37.5 22 37 8.5 27.5 15" fill={fill('--raw-home-icon-box-left')} />
        <ellipse cx="24" cy="27" rx="14.5" ry="12.5" fill={fill('--raw-home-icon-box-top')} />
        <path d="M21 31.5c1 1.6 5 1.6 6 0" />
        {!outline && <path d="M22.6 32.6v1.6a1.4 1.4 0 0 0 2.8 0v-1.6" fill="var(--raw-home-icon-roof)" />}
      </g>
      <circle cx="18.5" cy="25.5" r="1.7" fill="currentColor" />
      <circle cx="29.5" cy="25.5" r="1.7" fill="currentColor" />
      <ellipse cx="24" cy="29" rx="2" ry="1.4" fill="currentColor" />
      {!outline && <circle cx="14.5" cy="30" r="2.2" fill="var(--raw-home-icon-roof)" opacity={0.35} />}
      {!outline && <circle cx="33.5" cy="30" r="2.2" fill="var(--raw-home-icon-roof)" opacity={0.35} />}
    </svg>
  );
}
