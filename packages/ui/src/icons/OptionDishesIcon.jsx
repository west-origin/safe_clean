// (코드에서 정함) 가정·원룸 청소 옵션 아이콘 — 설거지. 홈 서비스 아이콘(ServiceStoreIcon·ServiceHomeIcon·ServiceStudioIcon)과 같은 플랫 컬러 형식 (2026-11-01 사용자 요청).
// 모양: 포개진 하늘색·회색 접시 위 주황 스펀지와 거품. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 BookingOptions (home 카테고리, FLAT_OPTION_ICONS.home 순번).

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function OptionDishesIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <ellipse cx="22" cy="36" rx="15" ry="5" fill="var(--raw-home-icon-door)" />
      <ellipse cx="22" cy="31" rx="15" ry="5" fill="var(--raw-home-icon-wall)" />
      <ellipse cx="22" cy="26" rx="15" ry="5" fill="var(--raw-home-icon-window)" />
      <rect x="28" y="10" width="14" height="8" rx="2" transform="rotate(-20 35 14)" fill="var(--raw-home-icon-box-left)" />
      <rect x="28" y="10" width="14" height="3.5" rx="1.5" transform="rotate(-20 35 14)" fill="var(--raw-home-icon-box-top)" />
      <circle cx="14" cy="15" r="3" fill="var(--raw-home-icon-window)" />
      <circle cx="20" cy="11" r="2" fill="var(--raw-home-icon-window)" />
    </svg>
  );
}
