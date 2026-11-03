// (코드에서 정함) '안심클린' 로고 — 2026-11-03 사용자 첨부 로그인 시안의 'kiwiplay' 자리. 로고 글자 에셋이 없어 글자로 그린다.
// 앱 글꼴(Noto Sans KR Bold) + 같은 색 얇은 둥근 외곽선으로 시안의 둥근 고딕에 가깝게. withMark 면 왼쪽에 로고 아이콘(BrandMarkIcon).
// SVG 라서 width 만 바꾸면 글자가 같이 커진다 (CSS 에 px 글자 크기를 쓰지 않으려는 것). 색은 currentColor.
// 글자 칸은 글자 폭에 딱 맞춘다(textLength) — 양옆 빈 여백이 없어야 아이콘과의 간격이 좁고 묶음 전체가 가운데에 온다.
// 쓰는 곳: 고객 앱 첫 화면(SignUp).
import BrandMarkIcon from '../../icons/BrandMarkIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './BrandWordmark.module.css';

const VIEW_W = 126; // 글자 칸 폭 (viewBox) — 32 크기 한글 4자 폭
const VIEW_H = 40;

/**
 * @param {object} props
 * @param {number} [props.width=140] 글자 폭(px). 높이는 비율대로, 아이콘은 글자 높이에 맞춘다
 * @param {boolean} [props.withMark] 왼쪽에 로고 아이콘
 * @param {string} [props.className]
 */
export default function BrandWordmark({ width = 140, withMark = false, className }) {
  const height = Math.round((width * VIEW_H) / VIEW_W);
  return (
    <span className={cx(styles.wrap, className)} role="img" aria-label="안심클린">
      {withMark && <BrandMarkIcon size={Math.round(height * 0.8)} />}
      <svg width={width} height={height} viewBox={`0 0 ${VIEW_W} ${VIEW_H}`} aria-hidden="true" focusable="false" className={styles.wordmark}>
        <text
          x="0.5"
          y="31"
          textLength={VIEW_W - 1}
          lengthAdjust="spacingAndGlyphs"
          className="text-heading-xl"
          fill="currentColor"
          stroke="currentColor"
          strokeWidth="0.5"
          strokeLinejoin="round"
        >
          안심클린
        </text>
      </svg>
    </span>
  );
}
