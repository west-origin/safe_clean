// Figma: Live/CircleControl (2160:2218) — 영상 위 오버레이형(반투명 검정 원). Type=Pause(72)|Back15(56)|Forward15(56)
// 하단바형(Live/LabeledControl)과 배경이 달라 분리돼 있다. 합치지 마라.
// 고객 앱 ServiceLivePortrait·ServiceLiveLandscape 가 녹화본(replay)일 때
// 15초 뒤로 / 재생·일시정지 / 15초 앞으로 버튼으로 쓴다.
// tone: 영상 위(가로 화면, 기본 onVideo)면 흰 포커스 테두리, 밝은 화면 배경 위(세로 화면, onLight)면 파란 포커스 테두리 (코드에서 정함).
import LivePauseIcon from '../../icons/LivePauseIcon.jsx';
import LivePlayIcon from '../../icons/LivePlayIcon.jsx';
import LiveBack15Icon from '../../icons/LiveBack15Icon.jsx';
import LiveForward15Icon from '../../icons/LiveForward15Icon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './LiveCircleControl.module.css';

// type 마다 스크린리더 라벨과 아이콘. style 이 있으면 그 이름의 CSS 클래스(크기)를 대신 쓴다.
/** 접근성 라벨은 Figma 에 없어 코드에서 정함 (PROGRESS.md) */
const TYPES = {
  pause: { label: '일시정지', Icon: LivePauseIcon },
  play: { label: '재생', Icon: LivePlayIcon, style: 'pause' }, // 멈춘 녹화본 — Pause 와 같은 72 원 (코드에서 정함)
  back15: { label: '15초 뒤로', Icon: LiveBack15Icon },
  forward15: { label: '15초 앞으로', Icon: LiveForward15Icon },
};

/**
 * @param {object} props
 * @param {'pause'|'play'|'back15'|'forward15'} props.type (Figma "Type" + play 는 코드에서 정함)
 * @param {() => void} [props.onClick]
 * @param {string} [props.ariaLabel] 기본 라벨을 바꿀 때 (예: 재생 상태에 따라 "재생")
 * @param {'onVideo'|'onLight'} [props.tone='onVideo'] 놓이는 배경. onVideo: 영상 위(흰 포커스 테두리) · onLight: 밝은 화면 배경 위(파란 포커스 테두리)
 * @param {string} [props.className]
 * 아이콘만 있는 원형 버튼을 그린다. 글자가 없어서 aria-label 로 이름을 붙인다.
 * 모르는 type 이면 바로 오류를 던져 오타를 개발 중에 드러낸다.
 */
export default function LiveCircleControl({ type, onClick, ariaLabel, tone = 'onVideo', className }) {
  const t = TYPES[type];
  if (!t) throw new Error(`LiveCircleControl: 알 수 없는 type "${type}"`);
  const { label, Icon } = t;
  return (
    <button type="button" className={cx(styles.control, styles[t.style ?? type], styles[tone], className)} aria-label={ariaLabel ?? label} onClick={onClick}>
      <Icon />
    </button>
  );
}
