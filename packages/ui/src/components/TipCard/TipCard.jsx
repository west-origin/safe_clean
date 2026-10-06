// Figma: Booking/NotesRequests › TipCard (2023:245) — brand/subtle 배경, 1px border/default, radius 20, 패딩 12/4, 간격 6. 아이콘 50×49 + 제목(Body/Bold brand) + 본문(Caption/Medium text/secondary)
// 고객 앱 청소 예약의 요청사항 화면(BookingNotesRequests)에서 요청사항 입력 카드 아래에 두는 안내 카드.
// <aside> 로 그려 보조기기에는 '보충 정보'(complementary) 영역으로 알리고, 제목을 그 영역의 이름으로 쓴다.
import { cx } from '../../utils/cx.js';
import styles from './TipCard.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string, width: number, height: number}} props.icon (ASSETS.requestTipIcon)
 * @param {string} props.title (Figma "이렇게 요청해보세요!")
 * @param {string} props.children 본문 (줄바꿈은 \n)
 * @param {string} [props.className]
 * 아이콘(장식이라 alt="") + 제목 + 본문을 가로로 놓는다. 본문 안의 줄바꿈 문자는 CSS(pre-line) 덕분에 그대로 줄이 바뀐다.
 */
export default function TipCard({ icon, title, children, className }) {
  return (
    <aside className={cx(styles.card, className)} aria-label={title}>
      <img className={styles.icon} src={icon.src} srcSet={icon.srcSet} width={icon.width} height={icon.height} alt="" />
      <div className={styles.content}>
        <p className={cx('text-body-bold', styles.title)}>{title}</p>
        <p className={cx('text-caption-medium', styles.body)}>{children}</p>
      </div>
    </aside>
  );
}
