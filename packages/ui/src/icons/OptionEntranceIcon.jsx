// (코드에서 정함) 매장 청소 옵션 아이콘 — 출입문 유리 닦기. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 문틀 안 하늘색 유리 문 두 짝, 흰 반사선, 손잡이. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (매장 카테고리, STORE_OPTION_ICONS 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionEntranceIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="8" y="6" width="32" height="38" rx="2" fill="var(--raw-home-icon-door)" />
      <rect x="11" y="9" width="12.5" height="32" rx="1" fill="var(--raw-home-icon-window)" />
      <rect x="24.5" y="9" width="12.5" height="32" rx="1" fill="var(--raw-home-icon-window)" />
      <path d="M14 20 19 15M14 25 21 18M27.5 20 32.5 15" stroke="var(--color-surface-default)" strokeWidth="1.8" strokeLinecap="round" />
      <rect x="20" y="24" width="2" height="7" rx="1" fill="var(--raw-home-icon-chimney)" />
      <rect x="26" y="24" width="2" height="7" rx="1" fill="var(--raw-home-icon-chimney)" />
    </svg>
  );
}
