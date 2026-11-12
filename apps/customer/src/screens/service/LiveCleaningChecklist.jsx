import { ProgressBar, CheckIcon, ScreenLoading, ErrorState, useAsync, usePolling, formatDateTime, cx } from '@ansimclean/ui';
import { getBooking, getLiveChecklist } from '../../api/customer.js';
import styles from './LiveCleaningChecklist.module.css';

// 고객은 매니저가 완료한 작업 상태를 확인한다. 장소 목록은 펼치거나 접을 수 있다.
export default function LiveCleaningChecklist({ bookingId, status }) {
  const { data, error, reload } = usePolling(() => getLiveChecklist(bookingId), [bookingId], { interval: 5000 });
  const { data: booking } = useAsync(() => getBooking(bookingId), [bookingId]);
  const stateLabel = status === 'ended' ? '종료' : status === 'paused' ? '일시 중단' : '진행 중';
  return <section className={styles.section} aria-label="청소 진행 체크리스트">
    <header className={styles.header}>
      <div className={styles.titleRow}>
        <h2 className="text-heading-s">청소 진행 체크리스트</h2>
        <span className={cx('text-caption-l-bold', styles.status)}>{stateLabel}</span>
      </div>
      {booking && <p className={cx('text-body-s', styles.secondary)}>{booking.serviceLabel} · {formatDateTime(booking.date, booking.time)}</p>}
    </header>
    {error ? <ErrorState error={error} onRetry={reload} /> : !data ? <ScreenLoading /> : <div className={styles.body}>
      <div className={styles.summary}>
        <div className={styles.summaryHead}>
          <div className={styles.summaryCopy}>
            <h3 className="text-title-card">전체 청소 진행률</h3>
            <p className={cx('text-body-s', styles.secondary)}>차근차근 완료할게요</p>
          </div>
          <strong className={cx('text-heading-xl', styles.percent)}>{data.percent}%</strong>
        </div>
        <ProgressBar value={data.done} max={data.total} label="전체 청소 진행률" className={styles.progress} />
        <div className={cx('text-body-s', styles.counts)}>
          <span>{data.done} / {data.total}개 작업 완료</span>
          <span>{Math.max(0, data.total - data.done)}개 남음</span>
        </div>
      </div>
      <div className={styles.placesHeading}>
        <h3 className="text-title-card">장소별 작업</h3>
        <span className={cx('text-caption-l', styles.secondary)}>매니저의 작업 현황</span>
      </div>
      {data.groups.map((group) => <details key={group.group} open className={styles.group}>
        <summary className={styles.groupHead}>
          <span className={styles.groupCopy}>
            <strong className="text-title-card">{group.group}</strong>
            <span className={cx('text-body-s', styles.secondary)}>세부 작업 {group.total}개</span>
          </span>
          <strong className="text-body-bold">{group.done} / {group.total}</strong>
          <span className={styles.chevron} aria-hidden="true" />
        </summary>
        <ul className={styles.items}>
          {group.items.map((item) => <li key={item.id} className={styles.item} aria-label={`${item.label} ${item.done ? '완료' : '미완료'}`}>
            <span className={cx(styles.checkbox, item.done && styles.checked)} aria-hidden="true">{item.done && <CheckIcon />}</span>
            <span className="text-body-default">{item.label}</span>
          </li>)}
        </ul>
      </details>)}
    </div>}
  </section>;
}
