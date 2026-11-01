// (코드에서 정함) 매장 청소 옵션 아이콘 — 진열대 먼지 제거. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 진열대 두 칸 위 주황·노랑·하늘색 상자, 오른쪽 위 반짝임. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (매장 카테고리, STORE_OPTION_ICONS 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionShelfIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="8" y="8" width="32" height="34" rx="2" fill="var(--raw-home-icon-wall)" />
      <rect x="8" y="23" width="32" height="3" fill="var(--raw-home-icon-door)" />
      <rect x="8" y="39" width="32" height="3" rx="1" fill="var(--raw-home-icon-door)" />
      <rect x="12" y="14" width="8" height="9" rx="1" fill="var(--raw-home-icon-box-left)" />
      <rect x="22" y="17" width="7" height="6" rx="1" fill="var(--raw-home-icon-box-top)" />
      <rect x="13" y="31" width="7" height="8" rx="1" fill="var(--raw-home-icon-window)" />
      <rect x="23" y="29" width="10" height="10" rx="1" fill="var(--raw-home-icon-box-right)" />
      <path d="M38 3.5 39.3 7.2 43 8.5 39.3 9.8 38 13.5 36.7 9.8 33 8.5 36.7 7.2Z" fill="var(--raw-home-icon-box-top)" />
    </svg>
  );
}
