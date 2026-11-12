// (코드에서 정함) 고객 앱 홈 서비스 아이콘 — 분해청소. 2026-11-08 사용자 첨부 홈 시안(플랫 컬러 후드 + 반짝임)을 따라 그렸다.
// 모양: 회색 주방 후드(굴뚝·몸통·하단 띠) + 파란 반짝임 3개. 색은 tokens/extra.css 의 --raw-home-icon-*, --raw-brand-mark-blue.
// 쓰는 곳: 고객 앱 Home 의 "분해청소" 버튼.

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServiceDisassemblyIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="20" y="5" width="8" height="10" rx="1" fill="var(--raw-home-icon-door)" />
      <path d="M17 15h14l7 10H10Z" fill="var(--raw-home-icon-wall)" />
      <rect x="8" y="24" width="32" height="4" rx="2" fill="var(--raw-home-icon-door)" />
      <path d="M13 32.5l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1Z" fill="var(--raw-brand-mark-blue)" />
      <path d="M25 31l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8Z" fill="var(--raw-brand-mark-blue)" />
      <path d="M35 33l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1Z" fill="var(--raw-brand-mark-blue)" />
    </svg>
  );
}
