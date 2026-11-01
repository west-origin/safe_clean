// 2026-10-31 사용자 첨부 서비스 주기 시안의 구역 나눔 띠 — Figma 에 없음 (코드에서 정함).
// 화면 가로를 꽉 채우는 8px 회색 띠. 보조기기에는 구분선(role=separator)으로 드러난다.
import { cx } from '../../utils/cx.js';
import styles from './SectionDivider.module.css';

/**
 * @param {object} props
 * @param {string} [props.className] 레이아웃용 (위아래 여백 등)
 */
export default function SectionDivider({ className }) {
  return <hr className={cx(styles.divider, className)} />;
}
