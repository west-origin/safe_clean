// (코드에서 정함) '안심클린' 글자 로고 — 2026-11-03 사용자 첨부 로그인 시안의 'kiwiplay' 자리. 로고 에셋이 없어 글자로 그린다.
// 시안 글꼴(둥근 굵은 고딕)에 가깝게: 앱 글꼴(Noto Sans KR Bold) + 같은 색 둥근 외곽선으로 획을 두껍고 둥글게 만든다.
// SVG 라서 width 만 바꾸면 글자가 같이 커진다 (CSS 에 px 글자 크기를 쓰지 않으려는 것). 색은 currentColor.
// 같은 날 추가: heavy={false} 면 외곽선을 얇게(조금 덜 굵게), withMark 면 왼쪽에 로고 아이콘(BrandMarkIcon).
// 쓰는 곳: 고객 앱 첫 화면(SignUp — withMark·얇게, 보관본 SignUpBlue — 기본).
import BrandMarkIcon from '../../icons/BrandMarkIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './BrandWordmark.module.css';

/**
 * @param {object} props
 * @param {number} [props.width=190] 글자 로고 폭(px). 높이는 비율대로 (아이콘은 글자 높이에 맞춘다)
 * @param {boolean} [props.heavy=true] true = 굵은 외곽선(파란 시안), false = 조금 얇게
 * @param {boolean} [props.withMark] 왼쪽에 로고 아이콘
 * @param {string} [props.className]
 */
export default function BrandWordmark({ width = 190, heavy = true, withMark = false, className }) {
  const height = Math.round((width * 44) / 150); // viewBox 비율
  return (
    <span className={cx(styles.wrap, className)} role="img" aria-label="안심클린">
      {withMark && <BrandMarkIcon size={Math.round(height * 0.8)} />}
      <svg width={width} height={height} viewBox="0 0 150 44" aria-hidden="true" focusable="false" className={styles.wordmark}>
        <text
          x="75"
          y="34"
          textAnchor="middle"
          className="text-heading-xl"
          fill="currentColor"
          stroke="currentColor"
          strokeWidth={heavy ? 1.6 : 0.5}
          strokeLinejoin="round"
          letterSpacing="-1"
        >
          안심클린
        </text>
      </svg>
    </span>
  );
}
