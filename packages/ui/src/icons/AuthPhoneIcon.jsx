// (코드에서 정함) 첫 화면 전화번호 칸 아이콘 — 휴대폰. 2026-11-03 사용자 첨부 시안의 편지봉투 아이콘과 같은 형태(가는 선)로 그렸다. 색은 currentColor.
// 쓰는 곳: AuthPillField icon="phone".

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AuthPhoneIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <rect x="6" y="2.5" width="12" height="19" rx="2.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M10.5 18.5h3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
