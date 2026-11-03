// (코드에서 정함) 내 예약 카드 — 2026-11-03 사용자 첨부 예약내역 시안(흰 카드 + 그림자):
//   윗줄: 서비스 이름(매장·가정·원룸 …) + 오른쪽 매칭 알약('매칭 중' / '매칭 완료', 브랜드 파랑 — 2026-11-03 사용자 요청으로 '배정' → '매칭')
//   파란 글자: 방문 일정 / 매니저 사진 + 이름(굵게) + 진행 상태(예약 완료·이동 중·청소 중·청소 완료) / 두 칸(시작·종료 시각)
//   구분선 아래: note(안내 한 줄) 또는 actions(회색 버튼들, tone="brand" 면 파랑 글자). 둘 다 없으면 구분선도 없다.
// 어떤 문구·버튼을 보일지는 화면(고객 앱 Reservations)이 정한다. 버튼은 콜백만 부른다.
import AssignPendingIcon from '../../icons/AssignPendingIcon.jsx';
import AssignDoneIcon from '../../icons/AssignDoneIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ReservationTicket.module.css';

const BADGES = {
  assigning: { label: '매칭 중', icon: <AssignPendingIcon /> },
  assigned: { label: '매칭 완료', icon: <AssignDoneIcon /> },
};

/**
 * @param {object} props
 * @param {string} props.title 서비스 이름 (예: '가정', '매장 · 정기 2/6회')
 * @param {'assigning'|'assigned'} [props.badge] 매칭 알약. 없으면 안 그린다 (예: 취소된 예약)
 * @param {string} props.schedule 파란 글자 일정 (예: '2026년 11월 20일 (금) 오전 10시')
 * @param {{ src: string, srcSet?: string }} props.avatar 매니저 사진
 * @param {string} props.manager 매니저 이름 줄 (예: '김미영 매니저', 배정 전이면 안내 문구 — badge='assigning' 이면 작은 회색 글자)
 * @param {string} [props.progress] 진행 상태 (예: '예약 완료'). 없으면 줄을 그리지 않는다 (매칭 전)
 * @param {{ label: string, value: string }[]} [props.times] 아래 두 칸 (예: 시작·종료)
 * @param {string} [props.note] 구분선 아래 안내 한 줄
 * @param {{ label: string, onClick: () => void, tone?: 'brand' }[]} [props.actions] 구분선 아래 버튼들 (note 가 있으면 무시)
 * @param {string} [props.className]
 */
export default function ReservationTicket({ title, badge, schedule, avatar, manager, progress, times = [], note, actions = [], className }) {
  const b = BADGES[badge];
  const hasFooter = Boolean(note) || actions.length > 0;
  return (
    <article className={cx(styles.card, className)} aria-label={title}>
      <div className={styles.top}>
        <div className={styles.heading}>
          <h3 className={cx('text-title-card', styles.title)}>{title}</h3>
          <p className={cx('text-body-s', styles.schedule)}>{schedule}</p>
        </div>
        {b && (
          <span className={cx('text-caption-l-bold', styles.badge)}>
            {b.icon}
            {b.label}
          </span>
        )}
      </div>

      <div className={styles.manager}>
        <img className={styles.avatar} src={avatar.src} srcSet={avatar.srcSet} width={56} height={56} alt="" />
        <div className={styles.managerText}>
          {/* 매칭 전이면 이름 대신 안내 문구(예: '매니저님을 매칭 중이에요') — 작은 회색 글자로 */}
          <p className={cx(badge === 'assigning' ? 'text-body-default' : 'text-heading-s', badge === 'assigning' ? styles.managerPending : styles.managerName)}>{manager}</p>
          {progress && <p className={cx('text-body-s', styles.progress)}>{progress}</p>}
        </div>
      </div>

      {times.length > 0 && (
        <dl className={styles.times}>
          {times.map((t) => (
            <div key={t.label} className={styles.time}>
              <dt className={cx('text-caption-l', styles.timeLabel)}>{t.label}</dt>
              <dd className={cx('text-body-m', styles.timeValue)}>{t.value}</dd>
            </div>
          ))}
        </dl>
      )}

      {hasFooter && <div className={styles.divider} aria-hidden="true" />}
      {note ? (
        <p className={cx('text-body-s', styles.note)}>{note}</p>
      ) : (
        actions.length > 0 && (
          <div className={styles.actions}>
            {actions.map((a) => (
              <button key={a.label} type="button" className={cx('text-body-bold', styles.action, a.tone === 'brand' && styles.actionBrand)} onClick={a.onClick}>
                {a.label}
              </button>
            ))}
          </div>
        )
      )}
    </article>
  );
}
