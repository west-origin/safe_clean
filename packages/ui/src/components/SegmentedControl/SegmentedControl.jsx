// Figma: Manager/Schedule › SegmentedControl (2124:2419) — surface/subtle 알약 안에 탭 3개(오늘/이번 주/달력). 활성 탭은 흰 알약.
// 매니저 앱 일정 화면(Schedule)의 기간 고르기(오늘 / 7일 / 달력)에 쓴다. 고른 key 는 부모가 들고 있고
// (Schedule 은 주소의 ?period= 에 둔다), 탭에 따라 아래 내용을 바꿔 그리는 것도 부르는 화면이 한다.
// 고객 앱의 밑줄 탭은 모양이 달라 UnderlineTabs 로 따로 있다.
import { cx } from '../../utils/cx.js';
import styles from './SegmentedControl.module.css';

/**
 * @param {object} props
 * @param {{ key: string, label: string }[]} props.options
 * @param {string} props.value 활성 탭 key
 * @param {(key: string) => void} [props.onChange]
 * @param {string} [props.ariaLabel]
 * @param {string} [props.className]
 */
export default function SegmentedControl({ options, value, onChange, ariaLabel, className }) {
  // APG 탭 패턴: 활성 탭만 Tab 순서에 들어가고 화살표로 옮긴다
  // (APG = W3C 의 접근성 작성 지침. 키보드 사용자는 Tab 한 번으로 탭 묶음에 들어오고 ←/→ 로 고른다)
  const onKeyDown = (e) => {
    const i = options.findIndex((o) => o.key === value);
    // 오른쪽 +1, 왼쪽 −1, Home = 맨 앞, End = 맨 뒤. 그 밖의 키는 0 → 아무것도 하지 않는다
    const delta = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : e.key === 'Home' ? -Infinity : e.key === 'End' ? Infinity : 0;
    if (!delta || options.length === 0) return;
    e.preventDefault(); // 화살표·Home/End 로 페이지가 스크롤되지 않게
    // 화살표는 끝에서 반대쪽 끝으로 돌아간다 (+ options.length 는 음수 나머지를 막는다)
    const next = delta === -Infinity ? 0 : delta === Infinity ? options.length - 1 : (i + delta + options.length) % options.length;
    onChange && onChange(options[next].key); // 초점만 옮기지 않고 선택도 바로 바꾸도록 부모에게 알린다
    // e.currentTarget 은 핸들러가 달린 tablist div. 새 탭 버튼으로 키보드 초점을 옮긴다
    e.currentTarget.querySelectorAll('[role="tab"]')[next]?.focus();
  };
  // role="tablist"/"tab" + aria-selected 로 보조기기가 탭 묶음으로 읽는다. 활성 탭만 tabIndex 0(Tab 으로 도달), 나머지는 −1.
  return (
    <div role="tablist" aria-label={ariaLabel} className={cx(styles.control, className)} onKeyDown={onKeyDown}>
      {options.map((opt) => {
        const active = opt.key === value;
        return (
          <button
            key={opt.key}
            type="button"
            role="tab"
            aria-selected={active}
            tabIndex={active ? 0 : -1}
            className={cx('text-title-small', styles.tab, active && styles.active)}
            onClick={() => onChange && onChange(opt.key)}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
