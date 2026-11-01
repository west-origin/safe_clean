// (코드에서 정함) 가정·원룸 청소 옵션 아이콘 — 세탁. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 세탁기 몸체, 진회색 조작부, 하늘색 둥근 창. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (home 카테고리, FLAT_OPTION_ICONS.home 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionLaundryIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="9" y="6" width="30" height="37" rx="3" fill="var(--raw-home-icon-wall)" />
      <rect x="9" y="6" width="30" height="8" rx="3" fill="var(--raw-home-icon-door)" />
      <circle cx="15" cy="10" r="1.6" fill="var(--raw-home-icon-chimney)" />
      <rect x="26" y="9" width="9" height="2.5" rx="1.25" fill="var(--raw-home-icon-chimney)" />
      <circle cx="24" cy="28" r="10" fill="var(--raw-home-icon-door)" />
      <circle cx="24" cy="28" r="7.5" fill="var(--raw-home-icon-window)" />
      <path d="M19.5 29c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0" stroke="var(--color-surface-default)" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}
