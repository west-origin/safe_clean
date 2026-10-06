// Figma: Reservations › SegmentTabs (2047:1695) — 밑줄형 탭. SegmentTab/Active = Body/Bold text/primary + 2px brand 밑줄, Default = Label/Default text/secondary.
// 탭 간격 26, 좌우 24/23, 아래 1px border/default. 매니저의 SegmentedControl(알약형)과 모양이 달라 분리.
// 고객 앱 예약 목록 화면(Reservations)의 상태 탭이 쓴다. 탭을 고르면 onChange(key) 만 부르고, 어떤 목록을 보일지는 그 화면이 정한다.
import { cx } from '../../utils/cx.js';
import styles from './UnderlineTabs.module.css';

/**
 * @param {object} props
 * @param {{ key: string, label: string }[]} props.options 라벨은 Figma 문구 그대로 (예: "예정 2")
 * @param {string} props.value 활성 탭 key
 * @param {(key: string) => void} [props.onChange]
 * @param {string} [props.ariaLabel]
 * @param {string} [props.className]
 * role="tablist"/"tab" + aria-selected 로 탭을 알린다. 활성 탭만 tabIndex 0 이라 Tab 키로는 활성 탭 하나에만 들어오고,
 * 탭 사이 이동은 ←/→ 키로 한다 (아래 onKeyDown).
 */
export default function UnderlineTabs({ options, value, onChange, ariaLabel, className }) {
  // 키보드: ←/→ 로 이웃 탭 선택 (포커스도 같이 옮긴다)
  const onKeyDown = (e) => {
    const delta = { ArrowLeft: -1, ArrowRight: 1 }[e.key];
    if (!delta || !onChange) return;
    e.preventDefault();
    const i = options.findIndex((o) => o.key === value);
    // 양 끝에서는 반대쪽 끝으로 돈다. + options.length 는 첫 탭에서 ← 를 눌러 -1 이 될 때 음수 나머지를 막는다
    const next = options[(i + delta + options.length) % options.length];
    onChange(next.key);
    // 같은 tablist 안에서 새 탭 버튼을 순서로 찾아 포커스를 옮긴다 (버튼 key 가 그대로라 다시 그려져도 같은 요소다)
    e.currentTarget.parentElement.querySelectorAll('[role="tab"]')[options.indexOf(next)]?.focus();
  };
  return (
    <div className={cx(styles.tabs, className)} role="tablist" aria-label={ariaLabel}>
      {options.map((o) => {
        const active = o.key === value;
        return (
          <button
            key={o.key}
            type="button"
            role="tab"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            onKeyDown={onKeyDown}
            className={cx(active ? 'text-body-bold' : 'text-label-default', styles.tab, active && styles.active)}
            onClick={() => onChange && onChange(o.key)}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
