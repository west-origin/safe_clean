// (코드에서 정함) 고객 앱 홈 서비스 아이콘 — 가정. 2026-10-31 사용자 첨부 시안(플랫 컬러 집)을 따라 그렸다.
// 모양: 굴뚝·빨간 지붕·회색 벽과 문. 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 고객 앱 Home 의 "가정" 버튼.

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServiceHomeIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="31" y="8" width="5" height="10" rx="1" fill="var(--raw-home-icon-chimney)" />
      <path d="M10 22 24 11l14 11v18H10Z" fill="var(--raw-home-icon-wall)" />
      <path d="M6.5 24 24 9.5 41.5 24" stroke="var(--raw-home-icon-roof)" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="19" y="27" width="10" height="13" rx="2" fill="var(--raw-home-icon-door)" />
    </svg>
  );
}
