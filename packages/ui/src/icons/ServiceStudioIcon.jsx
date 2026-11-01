// (코드에서 정함) 고객 앱 홈 서비스 아이콘 — 원룸. 2026-10-31 사용자 요청: 첨부 시안의 주황 박스 모양.
// 모양: 세 면의 밝기가 다른 정육면체 + 오른면의 흰 창. 색은 tokens/extra.css 의 --raw-home-icon-box-*. 쓰는 곳: 고객 앱 Home 의 "원룸" 버튼.

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServiceStudioIcon({ size = 48, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M24 6 40 15 24 24 8 15Z" fill="var(--raw-home-icon-box-top)" />
      <path d="M8 15 24 24V42L8 33Z" fill="var(--raw-home-icon-box-left)" />
      <path d="M24 24 40 15V33L24 42Z" fill="var(--raw-home-icon-box-right)" />
      <path d="M28 26.5 36 22v5.5L28 32Z" fill="var(--color-surface-default)" />
    </svg>
  );
}
