// Figma: Auth/ProfileHousing › HousingTypeItem (2064:1804, 82×82 카드 + Label/S, 간격 4) · PetItem (2064:1823, 170×120 카드 + Label/S, 간격 13)
// 3D 래스터 아이콘이 든 선택 카드. Figma 에는 Default 만 있어 Selected 는 코드에서 정함: brand/primary 1.5px 테두리 + brand/subtle 배경 (PROGRESS.md 판단 항목).
// 쓰는 곳 예: 고객 앱 회원가입의 ProfileHousing — 주거 형태(sm)와 반려동물(lg) 고르기. 화면이 카드들을
// role="radiogroup" 으로 감싸고, 어느 카드가 골라졌는지(selected)도 화면이 들고 있다.
import { cx } from '../../utils/cx.js';
import styles from './ImageOption.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string, width: number, height: number}} props.image ASSETS 의 래스터
 * @param {string} props.label (Figma "Label". 줄바꿈은 \n)
 * @param {boolean} props.selected
 * @param {() => void} [props.onSelect]
 * @param {'sm'|'lg'} [props.size='sm'] sm=HousingTypeItem(82×82), lg=PetItem(170×120)
 * @param {boolean} [props.disabled]
 * @param {string} [props.className]
 */
// 이미지 카드 + 라벨로 된 선택지 하나. 누르면 onSelect 만 부르고 선택 상태는 바꾸지 않는다(부모가 selected 로 내려 준다).
export default function ImageOption({ image, label, selected, onSelect, size = 'sm', disabled = false, className }) {
  // 모양은 버튼이지만 여러 개 중 하나를 고르는 것이라 role="radio" + aria-checked 로 선택 상태를 알린다.
  // data-state 는 선택 여부를 DOM 에 드러내는 표시다 (스타일은 .selected 클래스가 맡는다).
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      data-state={selected ? 'selected' : 'default'}
      disabled={disabled}
      onClick={onSelect}
      className={cx(styles.option, styles[size], selected && styles.selected, className)}
    >
      <span className={styles.card}>
        <img src={image.src} srcSet={image.srcSet} width={image.width} height={image.height} alt="" />
      </span>
      <span className={cx('text-label-s', styles.label)}>{label}</span>
    </button>
  );
}
