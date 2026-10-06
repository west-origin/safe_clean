// 로딩 스켈레톤. Figma 에 없음 — 스펙이 코드에서 판단하라고 한 항목. surface/subtle 바탕 + 은은한 반짝임.
// 데이터가 오기 전 자리를 잡아 두는 회색 막대. 예: 공용 UI ScreenLoading(화면 전체 로딩),
// 매니저 앱 Schedule 의 부분 로딩.
import { cx } from '../../utils/cx.js';
import styles from './Skeleton.module.css';

/**
 * 스켈레톤 막대 하나. 크기·모서리만 받고 글자는 없다.
 * @param {object} props
 * @param {string|number} [props.width='100%'] CSS 폭
 * @param {string|number} [props.height=16] CSS 높이(px 또는 문자열)
 * @param {'xs'|'sm'|'md'|'pill'} [props.radius='sm'] radius 토큰
 * @param {string} [props.className]
 */
export default function Skeleton({ width = '100%', height = 16, radius = 'sm', className }) {
  // 크기는 부르는 곳마다 달라 CSS 파일 대신 style 로 넣는다. 숫자는 px 로 바꾸고, radius 는 토큰 변수 이름으로 만든다.
  const style = {
    width: typeof width === 'number' ? `${width}px` : width,
    height: typeof height === 'number' ? `${height}px` : height,
    borderRadius: `var(--radius-${radius})`,
  };
  // aria-hidden: 의미 없는 장식이라 화면 낭독기가 읽지 않게 숨긴다
  return <span className={cx(styles.skeleton, className)} style={style} aria-hidden="true" />;
}
