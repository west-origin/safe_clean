// 아이콘만 있는 버튼 (Figma: LivePortrait › Button/Back·Button/Settings·Control/Fullscreen, LiveLandscape › Control/Mute·Control/Fullscreen).
// 시각 크기는 아이콘 크기(22/24), 터치 영역은 ::before 로 44. tone: dark=text/primary 아이콘(흰 배경 위), onVideo=흰 아이콘(영상 위)
// 쓰는 곳 예: 고객 앱 라이브 화면 ServiceLivePortrait(뒤로가기), ServiceLiveLandscape(음소거).
// 글자가 없어서 label 을 aria-label 로 붙여 스크린리더가 버튼 이름을 읽게 한다.
// 이 컴포넌트의 테스트는 따로 없고 LiveProgress/LiveProgress.test.jsx 에 함께 있다.
import { cx } from '../../utils/cx.js';
import styles from './IconButton.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.icon 아이콘 컴포넌트
 * @param {string} props.label 접근성 라벨
 * @param {() => void} [props.onClick]
 * @param {'dark'|'onVideo'} [props.tone='dark']
 * @param {boolean} [props.pressed] 토글형(음소거 등)이면 aria-pressed
 * @param {boolean} [props.disabled] 동작 없음 (흐리게)
 * @param {string} [props.className]
 */
// 아이콘 하나짜리 <button>. 아이콘 색은 tone 클래스의 color 를 따른다.
// pressed 를 안 넘기면(undefined) aria-pressed 속성이 빠져 토글이 아닌 보통 버튼으로 읽힌다.
export default function IconButton({ icon, label, onClick, tone = 'dark', pressed, disabled = false, className }) {
  return (
    <button type="button" className={cx(styles.button, styles[tone], className)} onClick={onClick} aria-label={label} aria-pressed={pressed} disabled={disabled}>
      {icon}
    </button>
  );
}
