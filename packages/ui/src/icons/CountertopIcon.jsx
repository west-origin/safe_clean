// (코드에서 정함) 주방 상판 모양 그림 — 2026-11-08 사용자 첨부 '대리석 코팅' 견적 안내 시안.
// 모양: 연회색 상판 + 회색 테두리 + 개수대 + 파란 반짝임. shape 로 1자·ㄱ자·ㄷ자. 쓰는 곳: PriceGuide '상판 모양별 요금'.

const PATHS = {
  i: 'M10 40h96v20H10Z',
  l: 'M14 18h78v52H74V36H14Z',
  u: 'M18 14h76v18H40v22h54v18H18Z',
};
const SINK = { i: [50, 44], l: [30, 22], u: [56, 18] };

/**
 * @param {object} props
 * @param {'i'|'l'|'u'} [props.shape='i'] 1자 / ㄱ자 / ㄷ자
 * @param {string} [props.className]
 */
export default function CountertopIcon({ shape = 'i', className }) {
  const [sx, sy] = SINK[shape] ?? SINK.i;
  return (
    <svg width="116" height="80" viewBox="0 0 116 80" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d={PATHS[shape] ?? PATHS.i} fill="var(--color-surface-subtle)" stroke="var(--raw-home-icon-door)" strokeWidth="3" strokeLinejoin="round" />
      <rect x={sx} y={sy} width="18" height="10" rx="4" fill="var(--color-surface-default)" stroke="var(--raw-home-icon-door)" strokeWidth="2" />
      <path d="M104 6l1.6 4 4 1.6-4 1.6-1.6 4-1.6-4-4-1.6 4-1.6Z" fill="var(--color-brand-primary)" />
    </svg>
  );
}
