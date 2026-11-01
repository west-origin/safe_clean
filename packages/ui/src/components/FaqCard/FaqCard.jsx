// 2026-10-31 사용자 첨부 서비스 주기 시안 '정기 서비스는?' 의 질문 카드 — Figma 에 없음 (코드에서 정함).
// 연한 브랜드 배경 카드 안에 회색 Q 동그라미 + 질문. 화면이 가로 스크롤 목록으로 여러 장 늘어놓는다.
import { cx } from '../../utils/cx.js';
import styles from './FaqCard.module.css';

/**
 * @param {object} props
 * @param {string} props.question 질문 문구
 * @param {string} [props.className] 레이아웃용 (폭 등)
 */
export default function FaqCard({ question, className }) {
  return (
    <article className={cx(styles.card, className)}>
      <span className={cx('text-heading-s', styles.badge)} aria-hidden="true">Q</span>
      <p className={cx('text-heading-s', styles.question)}>{question}</p>
    </article>
  );
}
