// (코드에서 정함) 후드 크기 그림 — 2026-11-08 사용자 첨부 '후드·덕트 분해청소' 견적 안내 시안을 따라 그렸다.
// 모양: 회색 굴뚝 + 연회색 후드 몸통(크기마다 폭이 넓어짐) + 진회색 하단 띠 + 파란 반짝임 3개.
// 쓰는 곳: PriceGuide 의 '후드 크기별 요금' 카드.

const WIDTH = { s: 34, m: 50, l: 68 }; // 후드 몸통 아래 폭의 절반 — 600 / 900 / 1200mm

/**
 * @param {object} props
 * @param {'s'|'m'|'l'} [props.size='m'] 후드 크기 (600 / 900 / 1200mm)
 * @param {string} [props.className]
 */
export default function HoodSizeIcon({ size = 'm', className }) {
  const w = WIDTH[size] ?? WIDTH.m;
  return (
    <svg width="144" height="88" viewBox="0 0 144 88" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="62" y="2" width="20" height="32" rx="3" fill="var(--raw-home-icon-door)" />
      <path d={`M${size === 's' ? 62 : 60} 34h${size === 's' ? 20 : 24}l${w - (size === 's' ? 10 : 12)} 26H${72 - w}Z`} fill="var(--raw-home-icon-wall)" />
      <rect x={72 - w} y="60" width={w * 2} height="8" rx="4" fill="var(--raw-home-icon-door)" />
      <path d="M50 74l1.6 4 4 1.6-4 1.6-1.6 4-1.6-4-4-1.6 4-1.6Z" fill="var(--color-brand-primary)" />
      <path d="M72 72l1 2.5 2.5 1-2.5 1-1 2.5-1-2.5-2.5-1 2.5-1Z" fill="var(--raw-home-icon-window)" />
      <path d="M94 74l1.6 4 4 1.6-4 1.6-1.6 4-1.6-4-4-1.6 4-1.6Z" fill="var(--color-brand-primary)" />
    </svg>
  );
}
