// Figma: Booking/Options › OptionItem (2058:1812 무료 · 2058:1819 유료) — 아이콘 박스 80×91(무료)/86(유료, 아이콘 아래 정렬) + 라벨 Title/Card (+ 가격 Title/Card text/secondary). 간격 13(무료)/10(유료).
// Figma 에 선택 상태가 없어 코드에서 정함: 아이콘 박스에 brand 1.5px 테두리 + brand/subtle 배경, radius 10 (PROGRESS.md 판단 항목)
//
// 3D 아이콘 + 이름(+ 가격)으로 된 추가 옵션 하나. 예: 고객 앱 예약의 옵션 선택 화면(BookingOptions)이
// 무료 옵션 줄과 유료 옵션 줄에 여러 개 놓는다. 여러 개를 켤 수 있는 체크박스처럼 동작한다.
// 선택 여부·비활성(무료 옵션 개수 제한)은 화면이 계산해 넘기고, 이 컴포넌트는 눌렸다는 것(onSelect)만 알린다.
import { cx } from '../../utils/cx.js';
import styles from './OptionItem.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string, width: number, height: number}} props.image ASSETS.bookingOptionIcons[category][i]
 * @param {string} props.label (Figma "Label", 줄바꿈은 \n)
 * @param {string} [props.price] 유료 옵션 가격 문구 (예: "+3,000원"). 있으면 유료형
 * @param {boolean} props.selected
 * @param {() => void} [props.onSelect]
 * @param {boolean} [props.disabled] 무료 옵션 택2 초과 등
 * @param {string} [props.className]
 */
export default function OptionItem({ image, label, price, selected, onSelect, disabled = false, className }) {
  // price 를 넘겼는지로 무료형/유료형을 가른다 (모양·간격이 조금 다르다 → CSS .free / .paid).
  const paid = price !== undefined;
  // role="checkbox" + aria-checked: 모양은 버튼이지만 보조기기가 "선택됨/선택 안 됨" 체크박스로 읽는다.
  // aria-label 은 라벨의 줄바꿈(\n)을 공백으로 바꾼 한 줄 이름이다. 유료면 가격까지 붙여 "이름 +3,000원" 으로 읽힌다.
  // 아이콘은 장식이라 alt="" 로 읽지 않게 한다.
  return (
    <button type="button" role="checkbox" aria-checked={selected} aria-label={paid ? `${label.replace(/\n/g, ' ')} ${price}` : label.replace(/\n/g, ' ')} disabled={disabled} onClick={onSelect} className={cx(styles.item, paid ? styles.paid : styles.free, selected && styles.selected, className)}>
      <span className={styles.iconBox}>
        <img src={image.src} srcSet={image.srcSet} width={image.width} height={image.height} alt="" />
      </span>
      <span className={styles.textBlock}>
        <span className={cx('text-title-card', styles.label)}>{label}</span>
        {paid && <span className={cx('text-title-card', styles.price)}>{price}</span>}
      </span>
    </button>
  );
}
