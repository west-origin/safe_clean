import { useId } from 'react';

/** 기존 앱 아이콘 중앙의 두 기하 문양만 벡터로 옮겼다. 배경·격자·그림자는 없다. */
export default function BrandMarkIcon() {
  const gradient = useId();
  return (
    <svg
      width="20"
      height="20"
      viewBox="234 234 556 556"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient
          id={gradient}
          x1="234"
          y1="790"
          x2="790"
          y2="234"

          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="var(--raw-brand-mark-light)" />
          <stop offset="1" stopColor="var(--raw-brand-mark-blue)" />
        </linearGradient>
      </defs>
      <path
        d="M440 234 581 375 722 234 790 302 649 443 790 584 722 652 372 302Z M302 372 652 722 584 790 443 649 302 790 234 722 375 581 234 440Z"
        fill={`url(#${gradient})`}
      />
    </svg>
  );
}
