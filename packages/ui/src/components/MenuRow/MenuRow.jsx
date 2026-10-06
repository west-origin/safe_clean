// Figma: Settings › MenuGroup › Rows › MenuRow (2048:1733 …) — 라벨(Body/Default) + 오른쪽 요소. 패딩 15/16, 아래 1px 구분선(마지막 행 제외).
//   chevron 행 (내 정보 수정 …): 높이 48, 오른쪽 Chevron 7×12 text/secondary
//   value 행  (버전 정보):       높이 48, 오른쪽 값 Body/XS text/secondary
//   toggle 행 (푸시 알림 …):     높이 56, 오른쪽 Toggle 44×26
// MenuGroup 안에 넣어 쓰는 설정 메뉴 한 줄(공용 UI). props 에 따라 세 모양 중 하나가 된다:
// checked 가 boolean 이면 토글 행, 아니고 value 가 있으면 값 행, 둘 다 아니면 chevron 버튼 행 (이 순서로 검사).
import ChevronRightTinyIcon from '../../icons/ChevronRightTinyIcon.jsx';
import Toggle from '../Toggle/Toggle.jsx';
import { cx } from '../../utils/cx.js';
import styles from './MenuRow.module.css';

/**
 * @param {object} props
 * @param {string} props.label 메뉴 문구 (Figma 그대로)
 * @param {() => void} [props.onClick] 있으면 chevron 행(버튼)
 * @param {string} [props.value] 있으면 값 행 (예: "v1.0.0")
 * @param {boolean} [props.checked] 있으면(boolean) 토글 행
 * @param {(checked: boolean) => void} [props.onToggle]
 * @param {string} [props.className]
 * 메뉴 한 줄을 그린다. 토글 행은 Toggle 의 label 로 같은 메뉴 문구를 넘겨 스위치의 이름이 되게 한다.
 */
export default function MenuRow({ label, onClick, value, checked, onToggle, className }) {
  // 꺼진 토글(false)도 토글 행이어야 하므로 값이 참인지가 아니라 타입으로 판단한다
  if (typeof checked === 'boolean') {
    return (
      <div className={cx(styles.row, styles.toggleRow, className)}>
        <span className={cx('text-body-default', styles.label)}>{label}</span>
        <Toggle checked={checked} onChange={onToggle} label={label} />
      </div>
    );
  }
  if (value !== undefined) { // 빈 문자열 값도 값 행으로 본다
    return (
      <div className={cx(styles.row, className)}>
        <span className={cx('text-body-default', styles.label)}>{label}</span>
        <span className={cx('text-body-xs', styles.value)}>{value}</span>
      </div>
    );
  }
  return (
    <button type="button" className={cx(styles.row, styles.button, className)} onClick={onClick}>
      <span className={cx('text-body-default', styles.label)}>{label}</span>
      <ChevronRightTinyIcon className={styles.chevron} />
    </button>
  );
}
