// (코드에서 정함) 가정·원룸 청소 옵션 아이콘 — 반려동물 배변 청소. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 회색 배변판 위 빨간 발바닥. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (home 카테고리, FLAT_OPTION_ICONS.home 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionPetCareIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="6" y="30" width="36" height="10" rx="3" fill="var(--raw-home-icon-door)" />
      <rect x="9" y="27" width="30" height="7" rx="2" fill="var(--raw-home-icon-wall)" />
      <ellipse cx="24" cy="19" rx="5" ry="4.2" fill="var(--raw-home-icon-roof)" />
      <circle cx="16.5" cy="13" r="2.4" fill="var(--raw-home-icon-roof)" />
      <circle cx="21" cy="9.5" r="2.4" fill="var(--raw-home-icon-roof)" />
      <circle cx="27" cy="9.5" r="2.4" fill="var(--raw-home-icon-roof)" />
      <circle cx="31.5" cy="13" r="2.4" fill="var(--raw-home-icon-roof)" />
    </svg>
  );
}
