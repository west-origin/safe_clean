// (코드에서 정함) 주의사항 선택 아이콘 — 아기 얼굴 (영유아 있음 / muted = 없음). 2026-11-03 사용자 요청으로 홈 서비스 아이콘과 같은 형식으로 다시 그렸다.
// 첨부 시안(홈의 매장·가정·원룸 아이콘) 패턴: 테두리 없는 플랫 면 + 그 팔레트(tokens/extra.css 의 --raw-home-icon-*) + 흰 디테일, 화면에서 연회색 원 위에 놓는다.
// muted 면 같은 모양을 회색 면으로 ('없음'). 쓰는 곳: 고객 앱 BookingNotesRequests (ChoiceCardGroup).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {boolean} [props.muted] 회색 면으로 ('없음' 표시)
 * @param {string} [props.className]
 */
export default function AnswerBabyIcon({ size = 48, muted = false, className }) {
  const face = muted ? 'var(--raw-home-icon-wall)' : 'var(--raw-home-icon-box-top)';
  const ear = muted ? 'var(--raw-home-icon-door)' : 'var(--raw-home-icon-box-left)';
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <circle cx="9.5" cy="26" r="4" fill={ear} />
      <circle cx="38.5" cy="26" r="4" fill={ear} />
      <circle cx="24" cy="26" r="15" fill={face} />
      <path d="M21 12c-1-3.5 1.5-6 4.5-5.5" stroke="var(--raw-home-icon-chimney)" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="18.5" cy="25" r="2" fill="var(--raw-home-icon-chimney)" />
      <circle cx="29.5" cy="25" r="2" fill="var(--raw-home-icon-chimney)" />
      <circle cx="24" cy="32.5" r="3.5" fill={muted ? 'var(--raw-home-icon-door)' : 'var(--raw-home-icon-roof)'} />
      <circle cx="24" cy="32.5" r="1.5" fill="var(--color-surface-default)" />
    </svg>
  );
}
