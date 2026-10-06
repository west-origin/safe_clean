// Figma(수리 예약): 요약 카드(1:468) — surface/subtle + 1px border/default + radius md, 패딩 16, 간격 12.
//   헤더: "현재 선택"(Title/Small) ↔ NoticeChip("3/4 단계", dots 아이콘, 폭 169) · 행: 라벨(Body/S secondary) ↔ 값(Body/Default primary), 행 간격 8
// 수리 예약 중간에 지금까지 고른 값(서비스·가전·사진 수 등)을 라벨/값 표로 보여 주는 카드. 보여 주기만 한다.
// 예: 고객 앱 RepairDetail 화면(rows 와 badge 는 화면이 만들어 넘긴다).
import NoticeChip from '../NoticeChip/NoticeChip.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SelectionSummary.module.css';

/**
 * 지금까지 고른 값 요약.
 * rows 는 위에서 아래 순서로 그린다. label 을 React key 로 쓰므로 행마다 달라야 한다.
 * @param {object} props
 * @param {string} [props.title='현재 선택']
 * @param {string} [props.badge] 오른쪽 칩 문구 (예: "3/4 단계")
 * @param {{ label: string, value: string }[]} props.rows
 * @param {string} [props.className]
 */
export default function SelectionSummary({ title = '현재 선택', badge, rows, className }) {
  return (
    <section className={cx(styles.card, className)} aria-label={title}>
      <div className={styles.header}>
        <h2 className={cx('text-title-small', styles.title)}>{title}</h2>
        {/* NoticeChip 을 onToggle 없이 단계 표시로만 쓴다. 선택 안 된(Default) 모양으로 고정, 눌러도 아무 일 없다 */}
        {badge && <NoticeChip label={badge} icon="dots" selected={false} className={styles.badge} />}
      </div>
      {/* dl/dt/dd: 라벨-값 짝을 나타내는 HTML 목록. div 로 한 행씩 묶어 가로 배치한다 */}
      <dl className={styles.rows}>
        {rows.map((r) => (
          <div key={r.label} className={styles.row}>
            <dt className={cx('text-body-s', styles.label)}>{r.label}</dt>
            <dd className={cx('text-body-default', styles.value)}>{r.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
