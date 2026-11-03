// (코드에서 정함) 첫 화면 비밀번호 칸 아이콘 — 열린 자물쇠. 2026-11-03 사용자 첨부 시안의 자물쇠 아이콘을 따라 가는 선으로 그렸다. 색은 currentColor.
// 쓰는 곳: AuthPillField icon="lock".

/**
 * @param {object} props
 * @param {number} [props.size=24] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function AuthLockIcon({ size = 24, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M8 10.5V7a4 4 0 0 1 7.6-1.7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="12" cy="15" r="6.5" stroke="currentColor" strokeWidth="1.5" />
      <path d="M12 13.5v3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}
