/** 고객용 문의 탭: 상담 헤드셋. */
export default function CustomerTabInquiryIcon({ size = 30, className }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 30 30"
      fill="none"
      aria-hidden="true"
      focusable="false"
      className={className}
    >
      <path
        d="M5 17v-3a10 10 0 0 1 20 0v6a5 5 0 0 1-5 5h-4"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
      />
      <rect x="3" y="13" width="6" height="9" rx="3" fill="currentColor" />
      <rect x="21" y="13" width="6" height="9" rx="3" fill="currentColor" />
      <rect x="12" y="23" width="7" height="4" rx="2" fill="currentColor" />
    </svg>
  );
}
