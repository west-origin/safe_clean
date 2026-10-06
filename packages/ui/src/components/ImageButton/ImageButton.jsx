// 이미지로 그려진 버튼. Figma 의 "Button/SocialLogin-*", "Button/SignUpWithPhone (flat image)" 처럼 글자까지 이미지에 포함된 경우.
// 글자가 이미지 안에 있으므로 label 로 접근성 이름을 반드시 준다.
// 쓰는 곳 예: 고객 앱 SignUp(카카오로 시작하기 등), SignUpComplete("지금 시작하기").
// image 는 공용 ASSETS 의 항목({ src, srcSet, width, height })을 그대로 받는다.
import { cx } from '../../utils/cx.js';
import styles from './ImageButton.module.css';

/**
 * @param {object} props
 * @param {{ src: string, srcSet?: string, width: number, height: number }} props.image ASSETS 항목
 * @param {string} props.label 이미지에 적힌 글자 (aria-label)
 * @param {() => void} [props.onClick]
 * @param {string} [props.className]
 */
// <button> 안에 이미지 한 장. 이미지 자체는 alt="" 로 숨기고 버튼 이름은 aria-label(label)로 준다.
// draggable="false": 길게 누르거나 끌 때 이미지가 끌려 나오지 않게 한다.
export default function ImageButton({ image, label, onClick, className }) {
  return (
    <button type="button" className={cx(styles.button, className)} aria-label={label} onClick={onClick}>
      <img src={image.src} srcSet={image.srcSet} width={image.width} height={image.height} alt="" draggable="false" />
    </button>
  );
}
