// (코드에서 정함) 매장 청소 옵션 아이콘 — 간판·외부 유리 청소. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 빨간 간판 아래 회색 벽과 하늘색 큰 유리창, 흰 반사선. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (매장 카테고리, STORE_OPTION_ICONS 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionSignboardIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="6" y="7" width="36" height="11" rx="2" fill="var(--raw-home-icon-roof)" />
      <rect x="12" y="11" width="24" height="3" rx="1.5" fill="var(--color-surface-default)" />
      <rect x="9" y="18" width="30" height="24" rx="1" fill="var(--raw-home-icon-wall)" />
      <rect x="12" y="22" width="24" height="16" rx="1" fill="var(--raw-home-icon-window)" />
      <path d="M16 32 22 26M20 34 28 26" stroke="var(--color-surface-default)" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
