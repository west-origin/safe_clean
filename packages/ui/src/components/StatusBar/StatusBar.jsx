// Figma: StatusBar (2009:24) — iOS 상태바 393×44. OS 가 그리는 영역이므로 시계·배터리는 그리지 않고 높이만 확보한다.
// 높이는 OS 가 준 상단 safe-area 값 그대로다 — 44 보다 작을 수도, 0 일 수도 있다 (Android 사정은 StatusBar.module.css).
// env() 값을 못 얻는 환경에서만 Figma 값 44 를 쓴다 (index.html viewport-fit=cover 전제).
// 예: 공용 UI ScreenLayout 이 화면 맨 위에 넣는다 (statusBar={false} 인 화면은 빼고).
import { cx } from '../../utils/cx.js';
import styles from './StatusBar.module.css';

/**
 * 상태바 높이만큼의 빈 여백. 내용이 없어 aria-hidden 으로 화면 낭독기에서 숨긴다.
 * @param {object} props
 * @param {string} [props.className]
 */
export default function StatusBar({ className }) {
  return <div className={cx(styles.statusBar, className)} aria-hidden="true" />;
}
