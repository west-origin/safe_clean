// Figma: Manager/CallCard (2112:2226) — 345×315. Expiry / Accept Label / Reject Label TEXT property.
// 나머지 문구(제목, 위치, 정산, 이동시간, 특이사항)는 인스턴스 오버라이드 → props.
//
// 콜(매니저에게 보내는 작업 제안) 한 건을 보여 주는 카드. 예: 매니저 앱 콜 목록(CallList, 여러 장)·
// 콜 상세(CallDetail, 한 장). 목록에서는 카드 아무 곳이나 누르면 상세로 가고(onOpen),
// 아래 거절·수락 버튼은 따로 눌린다. 문구는 화면이 api 로 받은 콜 데이터(call.title 등)에서 꺼내 넘긴다.
import Button from '../Button/Button.jsx';
import InfoPinIcon from '../../icons/InfoPinIcon.jsx';
import InfoClockIcon from '../../icons/InfoClockIcon.jsx';
import TimerIcon from '../../icons/TimerIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './CallCard.module.css';

/**
 * @param {object} props
 * @param {string} [props.eyebrow='새로운 청소 요청'] 상단 파란 문구
 * @param {string} props.expiry 만료 칩 문구 (Figma "Expiry", 예: "4:32 후 만료")
 * @param {string} props.title 일시 (예: "9월 18일 목요일 오후 2:00")
 * @param {string} props.location 위치·서비스 (예: "서울 강남구 역삼동 · 아파트 기본청소")
 * @param {string} [props.payLabel='예상 정산']
 * @param {string} props.pay 금액 (예: "85,000원")
 * @param {string} [props.eta] 이동 시간 (예: "현재 위치 기준 이동 약 25분"). 없으면 그 줄을 그리지 않는다 (거리 계산 전)
 * @param {string[]} [props.details] 수락 전에 알아야 할 것 — 예상 소요·주거 정보·청소 범위 (사용성 검토 2026-09-22)
 * @param {string} [props.note] 특이사항 (예: "특이사항: 반려묘 1마리")
 * @param {string} [props.acceptLabel='수락'] (Figma "Accept Label")
 * @param {string} [props.rejectLabel='거절'] (Figma "Reject Label")
 * @param {() => void} [props.onAccept]
 * @param {() => void} [props.onReject]
 * @param {() => void} [props.onOpen] 카드 자체를 탭했을 때 (Figma 프로토타입: CallCard → Call/Detail). 버튼 영역은 제외
 * @param {boolean} [props.loading] 수락 진행 중 (수락 버튼 스피너)
 * @param {boolean} [props.disabled] 다른 카드를 수락하는 중 — 버튼 비활성
 * @param {string} [props.className]
 * onOpen 을 넘기지 않으면(상세 화면) 카드 탭 영역을 만들지 않는다.
 */
export default function CallCard({
  eyebrow = '새로운 청소 요청',
  expiry,
  title,
  location,
  payLabel = '예상 정산',
  pay,
  eta,
  details,
  note,
  acceptLabel = '수락',
  rejectLabel = '거절',
  onAccept,
  onReject,
  onOpen,
  loading = false,
  disabled = false,
  className,
}) {
  // 카드 전체 탭(onOpen): 버튼 안에 버튼을 넣을 수 없어서(HTML 규칙) 카드 전체를 덮는 투명 버튼을 따로 깔고,
  // 거절·수락 줄(.actions)만 CSS z-index 로 그 위에 올린다.
  // 수락 진행 중(이 카드든 다른 카드든)에는 탭 영역도 막는다.
  return (
    <article className={cx(styles.card, className)}>
      {onOpen && <button type="button" className={styles.openOverlay} onClick={onOpen} disabled={loading || disabled} aria-label={`${title} 요청 상세 보기`} />}
      <div className={styles.eyebrowRow}>
        <span className={cx('text-label-s', styles.eyebrow)}>{eyebrow}</span>
        <span className={cx('text-title-xs', styles.expiry)}>
          <TimerIcon />
          {expiry}
        </span>
      </div>
      <h3 className={cx('text-title-card', styles.title)}>{title}</h3>
      <div className={styles.iconRow}>
        <InfoPinIcon size={18} className={styles.icon} />
        <span className={cx('text-body-default', styles.location)}>{location}</span>
      </div>
      <hr className={styles.divider} />
      <div className={styles.payRow}>
        <span className={cx('text-body-s', styles.payLabel)}>{payLabel}</span>
        <span className={cx('text-heading-s', styles.pay)}>{pay}</span>
      </div>
      {eta && (
        <div className={styles.iconRow}>
          <InfoClockIcon size={18} className={styles.icon} />
          <span className={cx('text-body-s', styles.eta)}>{eta}</span>
        </div>
      )}
      {details?.length > 0 && (
        <ul className={styles.details}>
          {details.map((d) => (
            <li key={d} className={cx('text-body-s', styles.detail)}>
              {d}
            </li>
          ))}
        </ul>
      )}
      {note && <p className={cx('text-body-s', styles.note)}>{note}</p>}
      <div className={styles.actions}>
        {/* 수락 중(loading)이면 거절도 막는다. disabled 는 목록에서 다른 카드를 수락하고 있을 때 */}
        <Button variant="secondary" onClick={onReject} disabled={disabled || loading}>
          {rejectLabel}
        </Button>
        <Button onClick={onAccept} loading={loading} disabled={disabled}>
          {acceptLabel}
        </Button>
      </div>
    </article>
  );
}
