// (코드에서 정함) 매장 청소 옵션 아이콘 — 주방 기름때 청소. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 후드 아래 진회색 프라이팬과 노란 기름방울. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (매장 카테고리, STORE_OPTION_ICONS 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionKitchenIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M18 6h12v8l8 8H10l8-8Z" fill="var(--raw-home-icon-door)" />
      <rect x="8" y="22" width="32" height="3" rx="1.5" fill="var(--raw-home-icon-wall)" />
      <ellipse cx="21" cy="36" rx="11" ry="5" fill="var(--raw-home-icon-chimney)" />
      <rect x="31" y="34.5" width="11" height="3" rx="1.5" fill="var(--raw-home-icon-chimney)" />
      <path d="M38 26c1.7 2.3 2.6 3.9 2.6 5a2.6 2.6 0 0 1-5.2 0c0-1.1.9-2.7 2.6-5Z" fill="var(--raw-home-icon-box-top)" />
      <path d="M19 28c1.2 1.7 1.8 2.8 1.8 3.6a1.8 1.8 0 0 1-3.6 0c0-.8.6-1.9 1.8-3.6Z" fill="var(--raw-home-icon-box-left)" />
    </svg>
  );
}
