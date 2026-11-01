// 2026-11-01 사용자 첨부 '옵션을 선택해 주세요.' 시안의 유료 옵션 카드 — Figma 에 없음 (코드에서 정함).
// 흰 카드: 왼쪽 위 아이콘, 오른쪽 위 진회색 + 원(고르면 브랜드 색 체크), 아래 이름과 가격. 두 장씩 나란히 놓는다.
import CheckIcon from '../../icons/CheckIcon.jsx';
import PlusIcon from '../../icons/PlusIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './PaidOptionCard.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.icon 왼쪽 위 그림
 * @param {string} props.label 옵션 이름 (줄바꿈 '\n' 은 공백으로 한 줄)
 * @param {string} [props.price] 가격 문구 (예: '+50,000원')
 * @param {boolean} props.selected
 * @param {() => void} [props.onToggle]
 * @param {string} [props.className]
 */
export default function PaidOptionCard({ icon, label, price, selected, onToggle, className }) {
  const name = label.replace(/\s*\n\s*/g, ' ').trim();
  return (
    <button type="button" role="checkbox" aria-checked={selected} aria-label={price ? `${name} ${price}` : name} className={cx(styles.card, selected && styles.selected, className)} onClick={onToggle}>
      <span className={styles.top} aria-hidden="true">
        <span className={styles.icon}>{icon}</span>
        <span className={styles.plus}>{selected ? <CheckIcon size={18} /> : <PlusIcon size={18} />}</span>
      </span>
      <span className={cx('text-body-default', styles.label)}>{name}</span>
      {price && <span className={cx('text-body-s', styles.price)}>{price}</span>}
    </button>
  );
}
