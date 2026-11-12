// (코드에서 정함) 고객 앱 홈 서비스 아이콘 — 사진 문의. 2026-11-08 사용자 첨부 홈 시안(파란 카메라)을 따라 그렸다.
// 모양: 브랜드 파란 카메라 몸통 + 흰 렌즈 테 + 하늘색 렌즈. 색은 토큰(--color-brand-primary, --color-surface-default)과 --raw-home-icon-window.
// 쓰는 곳: 고객 앱 Home 의 "사진 문의" 버튼.

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServicePhotoIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M17 13.5 19 10h10l2 3.5h7a3 3 0 0 1 3 3V36a3 3 0 0 1-3 3H10a3 3 0 0 1-3-3V16.5a3 3 0 0 1 3-3Z" fill="var(--color-brand-primary)" />
      <rect x="11" y="10" width="5" height="3" rx="1" fill="var(--color-brand-primary)" />
      <circle cx="24" cy="26" r="8.5" fill="var(--color-surface-default)" />
      <circle cx="24" cy="26" r="5.5" fill="var(--raw-home-icon-window)" />
      <circle cx="22" cy="24" r="1.8" fill="var(--color-surface-default)" />
    </svg>
  );
}
