// (코드에서 정함) 고객 앱 홈 서비스 아이콘 — 매장. 2026-10-31 사용자 첨부 시안(플랫 컬러)과 같은 색으로 그렸다 (시안에 매장 아이콘은 없다).
// 모양: 빨간 차양·회색 벽과 문·하늘색 창. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 Home 의 "매장" 버튼.

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServiceStoreIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="9" y="19" width="30" height="22" rx="2" fill="var(--raw-home-icon-wall)" />
      <path d="M7 12a2 2 0 0 1 2-2h30a2 2 0 0 1 2 2v6a4.25 4.25 0 0 1-8.5 0 4.25 4.25 0 0 1-8.5 0 4.25 4.25 0 0 1-8.5 0 4.25 4.25 0 0 1-8.5 0Z" fill="var(--raw-home-icon-roof)" />
      <rect x="13" y="27" width="9" height="14" rx="1.5" fill="var(--raw-home-icon-door)" />
      <rect x="26" y="27" width="9" height="7" rx="1.5" fill="var(--raw-home-icon-window)" />
    </svg>
  );
}
