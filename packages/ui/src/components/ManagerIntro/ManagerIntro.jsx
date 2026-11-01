// 2026-10-31 사용자 첨부 날짜/시간 시안 맨 위 '교육받은 매니저님이 방문합니다.' 줄 — Figma 에 없음 (코드에서 정함).
// 왼쪽: 안내 한 줄 + 회색 링크 버튼(>), 오른쪽: 매니저 그림(회색 원 안). 그림은 기존 래스터 ASSETS.managerAvatar.
import { ASSETS } from '../../assets/index.js';
import ChevronRightSmallIcon from '../../icons/ChevronRightSmallIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ManagerIntro.module.css';

/**
 * @param {object} props
 * @param {string} props.title 안내 한 줄
 * @param {string} [props.linkLabel] 회색 링크 글자 (예: '어떤 교육을 받나요?')
 * @param {() => void} [props.onLink]
 * @param {string} [props.className]
 */
export default function ManagerIntro({ title, linkLabel, onLink, className }) {
  const art = ASSETS.managerAvatar;
  return (
    <div className={cx(styles.intro, className)}>
      <div className={styles.text}>
        <p className={cx('text-body-m', styles.title)}>{title}</p>
        {linkLabel && (
          <button type="button" className={cx('text-body-m', styles.link)} onClick={onLink}>
            {linkLabel}
            <ChevronRightSmallIcon />
          </button>
        )}
      </div>
      <span className={styles.art}>
        <img src={art.src} srcSet={art.srcSet} width="56" height="56" alt="" />
      </span>
    </div>
  );
}
