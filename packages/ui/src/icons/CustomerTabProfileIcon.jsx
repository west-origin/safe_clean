/** 고객용 프로필 탭. */
export default function CustomerTabProfileIcon({ size = 30, className }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 30 30"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <circle cx="15" cy="9" r="5.5" />
      <path d="M4 26v-3a11 11 0 0 1 22 0v3H4Z" />
    </svg>
  );
}
