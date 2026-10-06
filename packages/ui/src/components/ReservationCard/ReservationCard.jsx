// Figma: Reservations › ReservationCard (2047:1706 · 2047:1727)
// 346 폭, 패딩 18, 간격 14, 1px border/default, radius 10. TopRow(제목 Title/Card + 상태 알약) / InfoList(라벨 52 + 값, 간격 7) / 구분선 / ActionRow(회색 버튼 2개 151×39).
// 상태 알약: scheduled = brand/subtle + brand 글자 "방문 예정", assigning = surface/subtle + text/secondary "매니저 배정 중" (Figma 문구 그대로)
// 고객 앱의 예약 카드. '내 예약' 화면(Reservations)이 목록으로 그리고, 홈(Home)은 다가오는 방문 한 장을
// changeable={false} 로 넘겨 변경·취소 버튼 대신 안내 문구를 보인다. 버튼은 콜백만 부르고, 이동·취소 요청은 화면이 한다.
import { RESERVATION_STATUS } from './status.js';
import { cx } from '../../utils/cx.js';
import styles from './ReservationCard.module.css';


/**
 * @param {object} props
 * @param {string} props.title 서비스 이름 (예: "가정/원룸 청소")
 * @param {'scheduled'|'assigning'|'inProgress'|'done'|'cancelled'} props.status
 * @param {{ label: string, value: string }[]} props.rows InfoList (일정 / 주소 / 매니저)
 * @param {boolean} [props.changeable=true] 지금 변경·취소할 수 있는가(서버: 시작 전 ∧ 24시간 전). false 면 버튼 대신 안내 한 줄 (코드에서 정함 — 시트를 열고 확인한 뒤에야 불가를 알던 것)
 * @param {string} [props.notChangeableNote='방문 24시간 전까지 변경·취소할 수 있어요'] changeable=false 일 때 문구
 * @param {() => void} [props.onChange] "예약 변경"
 * @param {() => void} [props.onCancel] "예약 취소"
 * @param {() => void} [props.onWatch] 진행 중일 때 "실시간 영상 보기" (Service/Start 진입점 — 코드에서 정함)
 * @param {boolean} [props.recordingReady] 완료 예약의 녹화본이 준비됐다 (B5, §9.2-8)
 * @param {() => void} [props.onReplay] 완료 + recordingReady 일 때 "녹화 영상 보기" (코드에서 정함)
 * @param {string} [props.className]
 */
export default function ReservationCard({ title, status, rows, changeable = true, notChangeableNote = '방문 24시간 전까지 변경·취소할 수 있어요', onChange, onCancel, onWatch, recordingReady = false, onReplay, className }) {
  const badge = RESERVATION_STATUS[status]; // { label, tone } — tone 은 CSS 의 .badge-<tone> 클래스(예: .badge-brand)가 된다
  // 모르는 status 면 빈 배지를 그리지 않고 오류를 던진다 — 데이터의 상태 이름이 어긋난 것을 바로 드러낸다
  if (!badge) throw new Error(`ReservationCard: 알 수 없는 status "${status}"`);
  // 정보 목록은 <dl>(라벨 dt + 값 dd, 한 줄씩 div 로 묶음). 구분선 아래 영역은 상태에 따라 하나만 그린다:
  //  방문 예정·배정 중 → 변경 가능하면 "예약 변경"/"예약 취소" 버튼, 아니면 버튼 대신 안내 한 줄
  //  진행 중 → "실시간 영상 보기" / 완료 + 녹화본 준비됨 → "녹화 영상 보기" / 그 밖(취소, 녹화 없는 완료) → 없음
  return (
    <article className={cx(styles.card, className)} aria-label={title}>
      <div className={styles.topRow}>
        <h3 className={cx('text-title-card', styles.title)}>{title}</h3>
        <span className={cx('text-caption-bold', styles.badge, styles[`badge-${badge.tone}`])}>{badge.label}</span>
      </div>
      <dl className={styles.infoList}>
        {rows.map((r) => (
          <div key={r.label} className={styles.infoRow}>
            <dt className={cx('text-body-default', styles.infoLabel)}>{r.label}</dt>
            <dd className={cx('text-body-default', styles.infoValue)}>{r.value}</dd>
          </div>
        ))}
      </dl>
      {(status === 'scheduled' || status === 'assigning') && !changeable && (
        <>
          <div className={styles.divider} aria-hidden="true" />
          <p className={cx('text-caption-l', styles.note)}>{notChangeableNote}</p>
        </>
      )}
      {(status === 'scheduled' || status === 'assigning') && changeable && (
        <>
          <div className={styles.divider} aria-hidden="true" />
          <div className={styles.actions}>
            <button type="button" className={cx('text-title-small', styles.action)} onClick={onChange}>
              예약 변경
            </button>
            <button type="button" className={cx('text-title-small', styles.action)} onClick={onCancel}>
              예약 취소
            </button>
          </div>
        </>
      )}
      {status === 'inProgress' && (
        <>
          <div className={styles.divider} aria-hidden="true" />
          <div className={styles.actions}>
            <button type="button" className={cx('text-title-small', styles.action, styles.actionBrand)} onClick={onWatch}>
              실시간 영상 보기
            </button>
          </div>
        </>
      )}
      {status === 'done' && recordingReady && (
        <>
          <div className={styles.divider} aria-hidden="true" />
          <div className={styles.actions}>
            <button type="button" className={cx('text-title-small', styles.action, styles.actionBrand)} onClick={onReplay}>
              녹화 영상 보기
            </button>
          </div>
        </>
      )}
    </article>
  );
}
