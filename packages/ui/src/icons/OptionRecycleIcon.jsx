// (코드에서 정함) 매장 청소 옵션 아이콘 — 분리배출. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 하늘색·주황 두 분리수거함과 진회색 뚜껑. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (매장 카테고리, STORE_OPTION_ICONS 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionRecycleIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M7 18h16l-1.6 23a2 2 0 0 1-2 1.9h-8.8a2 2 0 0 1-2-1.9Z" fill="var(--raw-home-icon-window)" />
      <path d="M25 18h16l-1.6 23a2 2 0 0 1-2 1.9h-8.8a2 2 0 0 1-2-1.9Z" fill="var(--raw-home-icon-box-right)" />
      <rect x="5" y="13" width="20" height="4" rx="2" fill="var(--raw-home-icon-chimney)" />
      <rect x="23" y="13" width="20" height="4" rx="2" fill="var(--raw-home-icon-chimney)" />
      <rect x="12" y="9" width="6" height="3" rx="1.5" fill="var(--raw-home-icon-door)" />
      <rect x="30" y="9" width="6" height="3" rx="1.5" fill="var(--raw-home-icon-door)" />
      <path d="M12 25v11M18 25v11M30 25v11M36 25v11" stroke="var(--color-surface-default)" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
