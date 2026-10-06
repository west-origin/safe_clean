// Figma: Manager/ListItem (2114:2239) — Label TEXT + 우측 chevron 18. 높이 52.
// 목록 한 줄(공용 UI). 예: 매니저 앱 Profile 화면의 메뉴 목록이 onClick 으로 쓴다.
// 이동하는 링크는 <a>, 동작하는 것은 <button> 으로 그려 스크린리더가 역할을 바르게 읽게 한다.
import ChevronRightSmallIcon from '../../icons/ChevronRightSmallIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ListItem.module.css';

/**
 * @param {object} props
 * @param {string} props.label (Figma "Label")
 * @param {() => void} [props.onClick] 있으면 <button>
 * @param {string} [props.href] 있으면 <a> (외부 링크). 앱 내 이동은 onClick + navigate 로
 * @param {string} [props.className]
 * 줄 하나를 그린다. href 가 없으면 onClick 이 없어도 <button> 이 된다.
 */
export default function ListItem({ label, onClick, href, className }) {
  // 라벨 + 오른쪽 chevron. <a> 와 <button> 두 모양에 똑같이 들어간다.
  const content = (
    <>
      <span className={cx('text-body-m', styles.label)}>{label}</span>
      <ChevronRightSmallIcon className={styles.chevron} />
    </>
  );
  if (href) {
    return (
      <a href={href} className={cx(styles.item, className)}>
        {content}
      </a>
    );
  }
  return (
    <button type="button" className={cx(styles.item, className)} onClick={onClick}>
      {content}
    </button>
  );
}
