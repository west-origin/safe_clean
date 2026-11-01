// (코드에서 정함) 가정·원룸 청소 옵션 아이콘 — 냉장고 정리 및 청소. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 두 칸 냉장고, 진회색 손잡이, 노란 반짝임. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (home 카테고리, FLAT_OPTION_ICONS.home 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionFridgeIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="12" y="5" width="22" height="38" rx="3" fill="var(--raw-home-icon-wall)" />
      <rect x="12" y="18" width="22" height="2" fill="var(--raw-home-icon-door)" />
      <rect x="15" y="9" width="2.4" height="6" rx="1.2" fill="var(--raw-home-icon-chimney)" />
      <rect x="15" y="23" width="2.4" height="9" rx="1.2" fill="var(--raw-home-icon-chimney)" />
      <path d="M39 6 40.3 9.7 44 11 40.3 12.3 39 16 37.7 12.3 34 11 37.7 9.7Z" fill="var(--raw-home-icon-box-top)" />
    </svg>
  );
}
