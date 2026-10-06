// Figma: Live/PillControl (2160:2227) — 영상 위 오버레이형 알약. Type=Capture(반투명 검정)|Exit(빨강 92%)
// 고객 앱 ServiceLiveLandscape(가로 전체 화면)가 영상 오른쪽 위에서 화면 캡처·종료하기 버튼으로 쓴다.
import LiveCaptureIcon from '../../icons/LiveCaptureIcon.jsx';
import LiveExitIcon from '../../icons/LiveExitIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './LivePillControl.module.css';

// type 마다 보이는 라벨(버튼 이름)과 아이콘.
/** 문구는 Figma 그대로 */
const TYPES = {
  capture: { label: '화면 캡처', Icon: LiveCaptureIcon },
  exit: { label: '종료하기', Icon: LiveExitIcon },
};

/**
 * @param {object} props
 * @param {'capture'|'exit'} props.type (Figma "Type")
 * @param {() => void} [props.onClick]
 * @param {string} [props.className]
 * 아이콘 + 글자 알약 버튼을 그린다. 글꼴 클래스(text-title-xs)는 버튼에 바로 붙인다. 모르는 type 이면 오류를 던진다.
 */
export default function LivePillControl({ type, onClick, className }) {
  const t = TYPES[type];
  if (!t) throw new Error(`LivePillControl: 알 수 없는 type "${type}"`);
  const { label, Icon } = t;
  return (
    <button type="button" className={cx('text-title-xs', styles.pill, styles[type], className)} onClick={onClick}>
      <Icon />
      {label}
    </button>
  );
}
