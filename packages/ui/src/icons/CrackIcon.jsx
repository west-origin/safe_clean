// (코드에서 정함) 대리석 크랙 그림 — 2026-11-08 사용자 첨부 '대리석 크랙 수리' 시안의 '이렇게 계산해요' 칸.
// 진한 남색 = 기본 요금 크랙, 파란색 = 추가 요금 크랙. 바탕은 옅은 물결 무늬 타일.
// type: long(일자 30cm 이상) · branch(일자 + 사선) · apart(떨어진 일자 2개) · branch2(일자 + 사선 2개). 쓰는 곳: PriceGuide

const WAVE = (x, y, n) => `M${x} ${y}` + " q3 -5 6 0 q3 5 6 0".repeat(n);

const ADDED = {
  long: <path d={WAVE(38, 38, 3)} />,
  branch: <path d="M44 38l6-8h-3l6-8h-3l6-8" />,
  apart: <path d={WAVE(26, 52, 3)} />,
  branch2: <><path d="M40 38l6-8h-3l6-8h-3l5-7" /><path d="M40 38l4 8h-3l4 8h3l4 6" /></>,
};

/**
 * @param {object} props
 * @param {'long'|'branch'|'apart'|'branch2'} [props.type='long']
 * @param {string} [props.className]
 */
export default function CrackIcon({ type = 'long', className }) {
  return (
    <svg width="72" height="72" viewBox="0 0 72 72" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="1" y="1" width="70" height="70" rx="14" fill="var(--color-surface-subtle)" stroke="var(--color-border-default)" strokeWidth="2" />
      <path d="M4 20q16-6 32 0t32 0M4 58q16-6 32 0t32 0" stroke="var(--color-border-default)" strokeWidth="1.5" />
      <g strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
        <path d={WAVE(type === 'apart' ? 10 : 8, type === 'apart' ? 24 : 38, type === 'long' ? 2 : 3)} stroke="var(--raw-home-ink)" />
        <g stroke="var(--color-brand-primary)">{ADDED[type] ?? ADDED.long}</g>
      </g>
    </svg>
  );
}
