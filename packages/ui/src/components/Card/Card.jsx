// 화면 안의 강조 카드 껍데기. Figma 에 컴포넌트로는 없고 화면마다 프레임으로 그려져 있어 공통화했다.
//   default: 1px border/default, 흰 배경                (Manager/Home › RemainingSchedule 등)
//   live:    1.5px status/live, 흰 배경                 (Manager/Home › InProgressHero)
//   brand:   1.5px brand/primary, brand/subtle 배경     (Manager/Home › NewCallAlert)
//   subtle:  테두리 없음, brand/subtle 배경              (Manager/Call/Empty › AvailabilityNote)
//   muted:   테두리 없음, surface/subtle 배경            (Call/Detail › NoteBlock, Job/Detail › AddressCard·RequestCard)
//   danger:  2px status/live, 흰 배경                   (Job/Preparing/Error › ErrorBanner)
//   warning: 1.5px status/warning, warning-subtle 배경  (Job/Paused › PausedHero)
//   warningMuted: 테두리 없음, warning-subtle 배경       (Job/InProgress › ActiveRequirements)
// 안쪽 레이아웃(간격·패딩)은 화면이 정한다.
// 두 앱의 여러 화면이 쓴다 (예: 매니저 콜 상세의 '고객 요청사항' = tone muted, 작업 준비의 촬영 장비 목록 = divided).
import { cx } from '../../utils/cx.js';
import styles from './Card.module.css';

/**
 * @param {object} props
 * @param {'default'|'live'|'brand'|'subtle'|'muted'|'danger'|'warning'|'warningMuted'} [props.tone='default']
 * @param {'none'|'xs'|'sm'|'md'|'list'|'note'|'alert'} [props.padding='md'] md=20, sm=16/18, xs=14/16, list=4/18, note=14/18, alert=18/20, none=0 (안쪽 패딩을 화면이 정할 때 — Auth/TermsConsent › AgreementCard)
 * @param {'none'|'xxs'|'xs'|'sm'|'smd'|'md'|'lg'} [props.gap='md'] 자식 세로 간격 xxs=4, xs=6, sm=8, smd=10, md=12, lg=14
 * @param {boolean} [props.divided] 자식 사이에 1px border/default 구분선 (Job/Preparing › CameraStatusCard)
 * @param {import('react').ReactNode} props.children
 * @param {string} [props.className]
 */
export default function Card({ tone = 'default', padding = 'md', gap = 'md', divided = false, children, className }) {
  // tone 값이 그대로 CSS 클래스 이름이 되고(styles.live 등), padding·gap 은 'pad-md'·'gap-sm' 같은 이름으로 바꿔 찾는다
  return (
    <section className={cx(styles.card, styles[tone], styles[`pad-${padding}`], styles[`gap-${gap}`], divided && styles.divided, className)}>
      {children}
    </section>
  );
}
