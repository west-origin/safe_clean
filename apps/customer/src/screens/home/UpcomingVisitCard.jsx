import { cx, ReservationMatchingBadge } from '@ansimclean/ui';
import styles from './UpcomingVisitCard.module.css';

export default function UpcomingVisitCard({ reservation, onClick }) {
  if (!reservation) {
    return <div className={styles.card}><p className={cx('text-body-default', styles.empty)}>다가오는 예약이 없어요</p></div>;
  }
  return (
    <button type="button" className={styles.card} onClick={onClick}>
      <span className={styles.heading}>
        <span className={cx('text-title-card', styles.title)}>{reservation.addressLabel}</span>
        <span className={cx('text-body-s', styles.schedule)}>{reservation.rows?.find((row) => row.label === '일정')?.value ?? `${reservation.date} ${reservation.time}`}</span>
      </span>
      <ReservationMatchingBadge badge={reservation.liveStarted ? 'live' : 'assigned'} />
    </button>
  );
}
