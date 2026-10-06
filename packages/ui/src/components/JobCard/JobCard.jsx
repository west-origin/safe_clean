// Figma: Manager/JobCard (2112:2207) — 345×254. 시간 + StatusBadge / 제목 / 구분선 / 정보 행 / Primary 버튼(CTA Label TEXT)
// 매니저 작업 카드(공용 UI). 위 순서대로 세로로 쌓는다.
// 지금 두 앱의 화면은 이 카드를 쓰지 않고, 개발용 갤러리(Gallery.jsx)에만 나온다.
import Button from '../Button/Button.jsx';
import StatusBadge from '../StatusBadge/StatusBadge.jsx';
import { cx } from '../../utils/cx.js';
import styles from './JobCard.module.css';

/**
 * @param {object} props
 * @param {string} props.time 예: "10:00"
 * @param {string} props.status StatusBadge 의 status (Figma 예시: scheduled)
 * @param {string} props.title 예: "강남구 역삼동 · 아파트 기본청소"
 * @param {{label: string, value: string}[]} props.rows 정보 행 (Figma 예시: 예상 소요시간 / 예상 정산)
 * @param {string} props.ctaLabel 버튼 문구 (Figma "CTA Label")
 * @param {() => void} [props.onCta]
 * @param {string} [props.className]
 * 카드 하나를 그린다. 버튼을 누르면 onCta 를 부를 뿐이고, 어디로 갈지는 부르는 쪽이 정한다.
 */
export default function JobCard({ time, status, title, rows = [], ctaLabel, onCta, className }) {
  // 정보 행은 label 을 React key 로 쓴다. 한 카드 안에서 라벨이 겹치면 React 가 경고한다.
  return (
    <article className={cx(styles.card, className)}>
      <div className={styles.topRow}>
        <span className={cx('text-heading-s', styles.time)}>{time}</span>
        <StatusBadge status={status} />
      </div>
      <h3 className={cx('text-title-card', styles.title)}>{title}</h3>
      <hr className={styles.divider} />
      {rows.map((row) => (
        <div key={row.label} className={cx('text-body-s', styles.infoRow)}>
          <span className={styles.infoLabel}>{row.label}</span>
          <span className={styles.infoValue}>{row.value}</span>
        </div>
      ))}
      <Button onClick={onCta}>{ctaLabel}</Button>
    </article>
  );
}
