// Figma: Auth/TermsConsent › Button/Back (2075:1809) — 화면 좌상단에 떠 있는 24px 화살표 (절대 위치, NavBar 없음).
// 터치 영역 44×44 안에 아이콘을 가운데 둔다. 위치(top/left)는 화면 CSS 가 정한다.
import BackArrowIcon from '../../icons/BackArrowIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './BackButton.module.css';

// 글자 없는 화살표 버튼. 보이는 글자가 없어서 aria-label 로 스크린리더가 읽을 이름을 준다.
// 고객 앱 가입·인증 화면이 onClick 에 navigate(-1) 을 넘겨 쓴다 (예: TermsConsent, ResetPassword).
/**
 * @param {object} props
 * @param {() => void} [props.onClick]
 * @param {string} [props.label='뒤로가기'] 접근성 라벨 (Figma 에 문구 없음)
 * @param {string} [props.className]
 */
export default function BackButton({ onClick, label = '뒤로가기', className }) {
  return (
    <button type="button" className={cx(styles.button, className)} onClick={onClick} aria-label={label}>
      <BackArrowIcon />
    </button>
  );
}
