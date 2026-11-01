// 2026-10-31 사용자 첨부 날짜/시간 시안의 하단 줄 — Figma 에 없음 (코드에서 정함).
// 왼쪽: 금액(굵게) + 회색 '자세히' 버튼, 오른쪽: 화면이 넘긴 버튼(children, 보통 "다음"). ScreenLayout 의 stickyCta 안에 넣는다.
import { cx } from '../../utils/cx.js';
import styles from './PriceCtaBar.module.css';

/**
 * @param {object} props
 * @param {string} props.priceLabel 금액 (예: '41,600원')
 * @param {string} [props.detailLabel='자세히']
 * @param {() => void} [props.onDetail] 있으면 금액 아래 버튼
 * @param {import('react').ReactNode} props.children 오른쪽 버튼
 * @param {string} [props.className]
 */
export default function PriceCtaBar({ priceLabel, detailLabel = '자세히', onDetail, children, className }) {
  return (
    <div className={cx(styles.bar, className)}>
      <div className={styles.price}>
        <span className={cx('text-heading-s', styles.amount)}>{priceLabel}</span>
        {onDetail && (
          <button type="button" className={cx('text-body-default', styles.detail)} onClick={onDetail}>
            {detailLabel}
          </button>
        )}
      </div>
      <div className={styles.action}>{children}</div>
    </div>
  );
}
