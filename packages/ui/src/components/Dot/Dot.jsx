// 상태 점 10px. Figma: Manager/Home › NewCallAlert › Ellipse(status/live), Manager/Call/Empty › StatusRow › Ellipse(status/success)
// 쓰는 곳 예: 매니저 앱 Home(새 청소 요청 알림 앞 점), CallList 빈 상태의 근무 상태 줄(콜을 받을 수 있으면 success, 미승인·정지면 live — getCalls 의 approved).
// 꾸밈이라 스크린리더에서 숨긴다(aria-hidden). 뜻은 옆 글자가 전하므로 색만으로 상태를 알리지 않는다.
import { cx } from '../../utils/cx.js';
import styles from './Dot.module.css';

/**
 * @param {object} props
 * @param {'live'|'success'} props.tone
 * @param {string} [props.className]
 */
// 둥근 점 하나. tone 이 곧 CSS 클래스 이름(.live / .success)이라 배경색이 거기서 정해진다.
export default function Dot({ tone, className }) {
  return <span className={cx(styles.dot, styles[tone], className)} aria-hidden="true" />;
}
