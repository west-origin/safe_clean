// Figma: Live/LabeledControl (2164:2257) — 하단바형(흰 배경 위, 라벨 포함). Type=Capture|Exit|Mute
// 오버레이형(Live/CircleControl, Live/PillControl)과 배경이 달라 분리돼 있다. 합치지 마라.
// 고객 앱 ServiceLivePortrait(세로 라이브 화면)의 영상 아래 줄에서 화면 캡처·종료하기·음소거(비활성) 버튼으로 쓴다.
import LiveCaptureLargeIcon from '../../icons/LiveCaptureLargeIcon.jsx';
import LiveHangUpIcon from '../../icons/LiveHangUpIcon.jsx';
import LiveMuteIcon from '../../icons/LiveMuteIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './LiveLabeledControl.module.css';

// type 마다 라벨(보이는 글자이자 버튼 이름)과 아이콘.
/** 문구는 Figma 그대로 */
const TYPES = {
  capture: { label: '화면 캡처', Icon: LiveCaptureLargeIcon },
  exit: { label: '종료하기', Icon: LiveHangUpIcon },
  mute: { label: '음소거', Icon: LiveMuteIcon },
};

/**
 * @param {object} props
 * @param {'capture'|'exit'|'mute'} props.type (Figma "Type")
 * @param {() => void} [props.onClick]
 * @param {boolean} [props.disabled] 동작 없음 (예: 바디캠은 영상만 보내므로 음소거가 없다 — 결정 §13-23). Figma 에 Disabled variant 는 없어 흐리게만
 * @param {string} [props.className]
 * 원형 아이콘 + 아래 라벨을 버튼 하나로 그린다. 라벨 글자가 버튼 안에 있어 따로 aria-label 이 없어도 이름이 된다.
 * 모르는 type 이면 바로 오류를 던진다.
 */
export default function LiveLabeledControl({ type, onClick, disabled = false, className }) {
  const t = TYPES[type];
  if (!t) throw new Error(`LiveLabeledControl: 알 수 없는 type "${type}"`);
  const { label, Icon } = t;
  return (
    <button type="button" className={cx(styles.control, styles[type], className)} onClick={onClick} disabled={disabled}>
      <span className={styles.circle}>
        <Icon />
      </span>
      <span className={cx('text-title-xs', styles.label)}>{label}</span>
    </button>
  );
}
