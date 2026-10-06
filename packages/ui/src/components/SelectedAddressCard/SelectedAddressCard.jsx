// Figma(수리 예약): 주소 카드(1:439) — 2px brand/primary 테두리 + radius sm, 패딩 20, 간격 16.
//   "현재 선택된 주소"(Title/Card) → [InfoIcon/Pin + "서비스 방문지"(Caption/L secondary)] → 주소 2줄(Body/Default)
// 수리 예약에서 지금 고른 방문 주소를 보여 주는 카드. 보여 주기만 하고 누를 곳은 없다.
// 예: 고객 앱 RepairAddress 화면(제목·안내 문구는 화면이 catalog.copy.address 에서 꺼내 넘긴다).
import InfoPinIcon from '../../icons/InfoPinIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SelectedAddressCard.module.css';

/**
 * 선택된 주소 카드. 도로명 주소 아래 상세 주소가 있으면 줄을 바꿔 붙인다.
 * @param {object} props
 * @param {string} [props.title='현재 선택된 주소']
 * @param {string} [props.caption='서비스 방문지']
 * @param {string} props.roadAddress
 * @param {string} [props.detailAddress]
 * @param {string} [props.className]
 */
export default function SelectedAddressCard({ title = '현재 선택된 주소', caption = '서비스 방문지', roadAddress, detailAddress, className }) {
  // section 에 aria-label 로 제목을 붙여 화면 낭독기가 '현재 선택된 주소' 영역으로 읽게 한다
  return (
    <section className={cx(styles.card, className)} aria-label={title}>
      <h2 className={cx('text-title-card', styles.title)}>{title}</h2>
      <div className={styles.info}>
        <div className={styles.head}>
          <InfoPinIcon className={styles.icon} />
          <span className={cx('text-caption-l', styles.caption)}>{caption}</span>
        </div>
        {/* 주소는 한 문단(p) 안에서 <br /> 로 두 줄. 상세 주소가 없으면 한 줄만 */}
        <p className={cx('text-body-default', styles.address)}>
          {roadAddress}
          {detailAddress && (
            <>
              <br />
              {detailAddress}
            </>
          )}
        </p>
      </div>
    </section>
  );
}
