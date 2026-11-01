// (코드에서 정함) 가정·원룸 청소 옵션 아이콘 — 침구교체. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 하늘색 이불을 덮은 회색 침대와 노란 베개. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (home 카테고리, FLAT_OPTION_ICONS.home 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionBeddingIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="6" y="14" width="4" height="26" rx="1.5" fill="var(--raw-home-icon-chimney)" />
      <rect x="6" y="28" width="36" height="8" rx="2" fill="var(--raw-home-icon-wall)" />
      <rect x="38" y="24" width="4" height="16" rx="1.5" fill="var(--raw-home-icon-chimney)" />
      <rect x="11" y="20" width="10" height="7" rx="3" fill="var(--raw-home-icon-box-top)" />
      <path d="M18 22h18a3 3 0 0 1 3 3v6H18Z" fill="var(--raw-home-icon-window)" />
    </svg>
  );
}
