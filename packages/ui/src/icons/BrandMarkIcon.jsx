import { useId } from 'react';

// 안심클린 로고 아이콘 — 기존 앱 아이콘 중앙의 두 기하 문양만 벡터로 옮겼다 (배경을 뺀 '누끼' 모양). 배경·격자·그림자는 없다.
// 색은 tokens/extra.css 의 --raw-brand-mark-*. 쓰는 곳: 고객 앱 Home 머리글, BrandWordmark(withMark).
// 2026-11-03 고객 앱 home 폴더에서 공용 UI 로 옮겼다 (첫 화면 로고에도 쓰려고).

/**
 * @param {object} props
 * @param {number} [props.size=20] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function BrandMarkIcon({ size = 20, className }) {
  const gradient = useId();
  return (
    <svg
      width={size}
      height={size}
      viewBox="234 234 556 556"
      fill="none"
      aria-hidden="true"
      focusable="false"
      className={className}
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
