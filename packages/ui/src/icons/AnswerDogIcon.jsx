// (코드에서 정함) 주의사항 선택 아이콘 — 강아지 얼굴 (반려동물 있음 / muted = 없음). 2026-11-03 사용자 요청으로 홈 서비스 아이콘과 같은 형식으로 다시 그렸다.
// 첨부 시안(홈의 매장·가정·원룸 아이콘) 패턴: 테두리 없는 플랫 면 + 그 팔레트(tokens/extra.css 의 --raw-home-icon-*) + 흰 디테일, 화면에서 연회색 원 위에 놓는다.
// muted 면 같은 모양을 회색 면으로 ('없음'). 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {boolean} [props.muted] 회색 면으로 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerDogIcon({ size = 48, muted = false, className }) {
  const face = muted ? 'var(--raw-home-icon-wall)' : 'var(--raw-home-icon-box-top)';
  const ear = muted ? 'var(--raw-home-icon-door)' : 'var(--raw-home-icon-box-left)';
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 24 9.5 7.5 21 13.5Z" fill={ear} strokeLinejoin="round" stroke={ear} strokeWidth="3" />
      <path d="M40 24 38.5 7.5 27 13.5Z" fill={ear} strokeLinejoin="round" stroke={ear} strokeWidth="3" />
      <ellipse cx="24" cy="27" rx="15" ry="13" fill={face} />
      <ellipse cx="24" cy="32" rx="7" ry="5.5" fill="var(--color-surface-default)" />
      <circle cx="18" cy="25" r="2" fill="var(--raw-home-icon-chimney)" />
      <circle cx="30" cy="25" r="2" fill="var(--raw-home-icon-chimney)" />
      <ellipse cx="24" cy="30" rx="2.4" ry="1.7" fill="var(--raw-home-icon-chimney)" />
      {!muted && <path d="M22.3 33h3.4v1.8a1.7 1.7 0 0 1-3.4 0Z" fill="var(--raw-home-icon-roof)" />}
    </svg>
  );
}
