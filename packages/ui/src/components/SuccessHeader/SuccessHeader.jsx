// Figma(수리 예약): 완료 피드백(1:239) — 완료 표시(80 원 accent/mint 안 44 원 20c983(원본색, extra.css --raw-success-green) + 흰 check 22) → 제목(Heading/M, 가운데) → 안내(Body/S secondary, 가운데). 간격 12 / 8
// 완료 화면 위쪽의 체크 원 + 제목 + 안내. 쓰는 곳: 고객 앱 수리 예약 완료 화면(screens/repair/booking/RepairComplete.jsx).
import CheckIcon from '../../icons/CheckIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './SuccessHeader.module.css';

/**
 * @param {object} props
 * @param {string} props.title
 * @param {string} [props.description]
 * @param {string} [props.className]
 */
// 체크 표시(꾸밈) 아래에 제목(h1)과 안내를 가운데 정렬로 그린다. description 이 비어 있으면 안내 줄은 없다.
export default function SuccessHeader({ title, description, className }) {
  return (
    <header className={cx(styles.header, className)}>
      {/* 바깥 연한 원(halo) 안에 초록 원 + 흰 체크. aria-hidden 이라 스크린리더는 건너뛰고 제목만 읽는다 */}
      <div className={styles.halo} aria-hidden="true">
        <div className={styles.circle}>
          <CheckIcon size={22} className={styles.check} />
        </div>
      </div>
      <div className={styles.message}>
        <h1 className={cx('text-heading-m', styles.title)}>{title}</h1>
        {description && <p className={cx('text-body-s', styles.description)}>{description}</p>}
      </div>
    </header>
  );
}
