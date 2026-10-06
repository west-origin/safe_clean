// Figma: Repair › RepairCategoryGrid (2073:1771) — 2행 × 4열. 행 좌우 18, 행 간격 6. 아이템: 아이콘 박스 45×52(아이콘 46 아래 정렬) + 라벨 Body/Default text/secondary, 간격 12.
// 문구·순서 Figma 그대로. 아이콘은 3D 래스터(ASSETS).
// 고객 앱 수리 탭(Repair 화면)의 카테고리 격자. 누르면 onSelect(key) 로 알리기만 하고,
// 어느 수리 예약 단계로 갈지는 화면이 정한다. 목록(문구·순서·그림)은 categories.js 에 있다.
import { REPAIR_CATEGORIES } from './categories.js';
import { cx } from '../../utils/cx.js';
import styles from './RepairCategoryList.module.css';


/**
 * @param {object} props
 * @param {(key: string) => void} [props.onSelect] REPAIR_CATEGORIES 의 key
 * @param {string} [props.className]
 * 칸마다 <button> 이고 그림은 장식(alt="")이라, 버튼 이름은 라벨 글자(예: "배관·누수")가 된다.
 */
export default function RepairCategoryList({ onSelect, className }) {
  return (
    <ul className={cx(styles.grid, className)} aria-label="수리 카테고리">
      {REPAIR_CATEGORIES.map((c) => (
        <li key={c.key}>
          <button type="button" className={styles.item} onClick={() => onSelect && onSelect(c.key)}>
            <span className={styles.iconBox}>
              <img src={c.image.src} srcSet={c.image.srcSet} width={c.image.width} height={c.image.height} alt="" />
            </span>
            <span className={cx('text-body-default', styles.label)}>{c.label}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}
