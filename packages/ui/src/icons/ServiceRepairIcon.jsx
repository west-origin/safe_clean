// (코드에서 정함) 고객 앱 수리 아이콘. 2026-11-08 사용자 첨부 '대리석 코팅' 견적 안내 시안의 아이콘(망치 + 스패너 교차)을 따라 그렸다.
// 모양: 회색 스패너(오른쪽 위 벌어진 입 → 왼쪽 아래 손잡이) 위에, 검은 머리·주황 손잡이 망치(왼쪽 위 머리 → 오른쪽 아래 손잡이)가 X 자로 겹친다.
// 색은 tokens/extra.css 의 --raw-home-icon-*. 쓰는 곳: 홈의 "수리" 버튼, PriceGuide 머리(icon="repair").
import { useId } from 'react';

/**
 * @param {object} props
 * @param {number} [props.size=48] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function ServiceRepairIcon({ size = 48, className }) {
  const jaw = useId(); // 스패너 입을 파내는 mask id (한 화면에 여러 개 그려도 겹치지 않게)
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true" focusable="false" className={className}>
      <mask id={jaw}>
        <rect width="48" height="48" fill="white" />
        {/* 스패너 머리에서 오른쪽 위로 벌어진 입 */}
        <rect x="-2.5" y="-6" width="5" height="9" fill="black" transform="translate(36 12) rotate(45)" />
      </mask>
      {/* 스패너: 머리(원) + 자루 */}
      <g mask={`url(#${jaw})`} fill="var(--raw-home-icon-door)">
        <circle cx="34" cy="14" r="7" />
      </g>
      <path d="M30 18 12.5 35.5" stroke="var(--raw-home-icon-door)" strokeWidth="5.5" strokeLinecap="round" />
      {/* 망치 손잡이 */}
      <path d="M19 19 36.5 36.5" stroke="var(--raw-home-icon-box-left)" strokeWidth="5" strokeLinecap="round" />
      {/* 망치 머리: 손잡이와 직각인 검은 덩어리 */}
      <rect x="-9" y="-5" width="18" height="10" rx="2" fill="var(--raw-home-icon-chimney)" transform="translate(15.5 15.5) rotate(-45)" />
    </svg>
  );
}
