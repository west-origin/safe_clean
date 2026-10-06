// Figma: Manager/NavBar (2121:2264) — 393×52, 뒤로가기 24px + 제목(Title TEXT property)
//
// 화면 맨 위 제목 줄. 상세·하위 화면이 ScreenLayout 의 navBar 슬롯에 넣어 쓴다
// (예: 매니저 앱 콜 상세 CallDetail, 고객 앱 공지사항 Notices).
// ScreenError 도 onBack 만 받았을 때 제목 없는 NavBar 를 만들어 뒤로가기를 보여 준다.
// 예약 단계 화면의 진행 표시 줄은 이것이 아니라 StepNavBar 다.
import BackIcon from '../../icons/BackIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './NavBar.module.css';

/**
 * @param {object} props
 * @param {string} props.title 화면 제목 (Figma "Title")
 * @param {() => void} [props.onBack] 뒤로가기. 없으면 버튼을 숨긴다
 * @param {import('react').ReactNode} [props.right] 오른쪽 슬롯 (Figma 에는 없음. 화면에서 필요할 때만)
 * @param {string} [props.className]
 * 화면 이동은 하지 않는다. 뒤로가기를 누르면 onBack 만 부르고, 어디로 갈지는 화면이 정한다(navigate).
 */
export default function NavBar({ title, onBack, right, className }) {
  // 제목은 h1 이다 — 화면 읽기 도구가 "이 화면의 제목" 으로 읽는다. 버튼은 아이콘뿐이라 aria-label 로 이름을 준다.
  return (
    <header className={cx(styles.navBar, className)}>
      {onBack && (
        <button type="button" className={styles.back} onClick={onBack} aria-label="뒤로가기">
          <BackIcon />
        </button>
      )}
      <h1 className={cx('text-title-card', styles.title)}>{title}</h1>
      {right && <div className={styles.right}>{right}</div>}
    </header>
  );
}
