// Figma: ServiceCategoryList (2024:257) — 홈 화면 서비스 카테고리 4종. 393×100, 좌우 18, 아이콘 위·라벨 아래.
// 카테고리 목록(키·문구·아이콘)은 categories.js 에 있고, 이 파일은 그 목록을 버튼으로 그리기만 한다.
// 예: 고객 앱 Home 화면. 누르면 onSelect(키) → Home 이 해당 카테고리의 예약 첫 화면(/booking/…/frequency)으로 보낸다.
import { SERVICE_CATEGORIES } from './categories.js';
import { cx } from '../../utils/cx.js';
import styles from './ServiceCategoryList.module.css';

/**
 * 홈의 서비스 카테고리 버튼 줄. 각 버튼은 3D 아이콘(위) + 라벨(아래).
 * onSelect 는 누른 카테고리의 키(categories.js 의 key)를 받는다.
 * @param {object} props
 * @param {(key: 'home'|'moveIn'|'appliance'|'office') => void} [props.onSelect]
 * @param {string} [props.className]
 */
export default function ServiceCategoryList({ onSelect, className }) {
  return (
    <ul className={cx(styles.list, className)} aria-label="서비스 카테고리">
      {SERVICE_CATEGORIES.map((c) => (
        <li key={c.key}>
          <button type="button" className={styles.item} onClick={() => onSelect && onSelect(c.key)}>
            {/* 아이콘은 장식이라 alt="" — 버튼 이름은 아래 라벨 글자가 된다. srcSet 으로 화면 밀도(1x/2x/3x)에 맞는 파일을 고른다 */}
            <span className={styles.iconBox}>
              <img src={c.src} srcSet={c.srcSet} width={c.width} height={c.height} alt="" />
            </span>
            <span className={cx('text-body-default', styles.label)}>
              {/* Figma 줄바꿈 그대로: 두 번째 줄부터 앞에 <br /> 를 넣는다 */}
              {c.lines.map((line, i) => (
                <span key={line} className={styles.line}>
                  {i > 0 && <br />}
                  {line}
                </span>
              ))}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
