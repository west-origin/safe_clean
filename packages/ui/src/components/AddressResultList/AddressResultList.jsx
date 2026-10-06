// Figma: Booking/AddressSearch › MapPreview (flat image) (92:398) 에 그려진 검색 결과 목록을 실제 목록으로 옮김.
// 행: 회색 원(32) 안 핀 아이콘 + 주소(Title/Small) + 부가 정보(Caption/L) + 우측 chevron. 높이 54, 아래 1px 구분선. 이미지에서 잰 값 (코드에서 정함)
import InfoPinIcon from '../../icons/InfoPinIcon.jsx';
import ChevronRightTinyIcon from '../../icons/ChevronRightTinyIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './AddressResultList.module.css';

// 주소 검색 결과 목록. 행 하나가 버튼이고, 누르면 onSelect(그 결과) 를 부른다.
// 고객 앱 BookingAddressSearch 화면이 결과가 있을 때 그리고, 고른 결과의 main(도로명주소)으로 다음 단계로 간다.
// results[].main 은 첫 줄(주소), sub 는 둘째 줄(동·건물명 등 — 없으면 줄을 그리지 않는다). id 는 React key.
/**
 * @param {object} props
 * @param {{ id: string, main: string, sub?: string }[]} props.results
 * @param {(result: { id: string, main: string, sub?: string }) => void} [props.onSelect]
 * @param {string} [props.className]
 */
export default function AddressResultList({ results, onSelect, className }) {
  return (
    <ul className={cx(styles.list, className)} aria-label="주소 검색 결과">
      {results.map((r) => (
        <li key={r.id}>
          <button type="button" className={styles.row} onClick={() => onSelect && onSelect(r)}>
            {/* 핀 아이콘 원은 장식이라 스크린리더에서 숨긴다 — 버튼 이름은 주소 글자로 읽힌다 */}
            <span className={styles.circle} aria-hidden="true">
              <InfoPinIcon size={16} />
            </span>
            <span className={styles.text}>
              <span className={cx('text-title-small', styles.main)}>{r.main}</span>
              {r.sub && <span className={cx('text-caption-l', styles.sub)}>{r.sub}</span>}
            </span>
            <ChevronRightTinyIcon className={styles.chevron} />
          </button>
        </li>
      ))}
    </ul>
  );
}
