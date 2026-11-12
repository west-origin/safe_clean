// (코드에서 정함) 선물 상자 아이콘 — 2026-11-08 사용자 첨부 견적 안내 시안의 '첫 결제 시 무료' 띠.
// 모양: 브랜드 파란 상자 + 뚜껑 + 리본. 리본 틈은 흰색. 쓰는 곳: PriceGuide 혜택 띠.

/**
 * @param {object} props
 * @param {number} [props.size=40] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function GiftBoxIcon({ size = 40, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M20 12c-2-5-9-7-10-3s5 4 10 3Zm0 0c2-5 9-7 10-3s-5 4-10 3Z" stroke="var(--color-brand-primary)" strokeWidth="2.5" strokeLinejoin="round" />
      <rect x="4" y="12" width="32" height="8" rx="2" fill="var(--color-brand-primary)" />
      <rect x="7" y="21" width="26" height="16" rx="2" fill="var(--color-brand-primary)" />
      <rect x="18.5" y="12" width="3" height="25" fill="var(--color-surface-default)" />
    </svg>
  );
}
