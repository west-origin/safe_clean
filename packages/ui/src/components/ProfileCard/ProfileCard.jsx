// Figma: Settings › ProfileCard (2048:1723) — Avatar 56 원(교체필요: 프로필 사진 → 회색 원 플레이스홀더) + 이름(Title/Card) + 전화번호(Body/S) + Chevron. 패딩 18, 간격 14, 1px border, radius 10.
// TODO(asset): 교체필요 — Avatar (2048:1724) 프로필 사진. 지금은 border/default 원.
//
// 프로필 요약 카드(사진 + 이름 + 전화번호 + 오른쪽 화살표). 카드 전체가 버튼 하나다.
// 화면 이동은 부르는 쪽 onClick 이 정한다 (예: 고객 앱 설정 화면 Settings 는 내 정보 수정 /settings/profile 로 보낸다).
import ChevronRightTinyIcon from '../../icons/ChevronRightTinyIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './ProfileCard.module.css';

/**
 * @param {object} props
 * @param {string} props.name 표시 이름 (예: "김하늘 님")
 * @param {string} props.phone (예: "010-1234-5678")
 * @param {string} [props.avatarSrc] 프로필 사진 URL. 없으면 회색 원
 * @param {() => void} [props.onClick]
 * @param {string} [props.className]
 */
export default function ProfileCard({ name, phone, avatarSrc, onClick, className }) {
  // 사진 주소가 있으면 사진, 없으면 같은 크기의 회색 원(aria-hidden: 보조기기는 건너뛴다).
  // 버튼의 접근성 이름은 안의 글자(이름 + 전화번호)가 이어진 것이 된다. 사진은 alt="" 라 이름에 들어가지 않는다.
  return (
    <button type="button" className={cx(styles.card, className)} onClick={onClick}>
      {avatarSrc ? <img className={styles.avatar} src={avatarSrc} width="56" height="56" alt="" /> : <span className={styles.avatar} aria-hidden="true" />}
      <span className={styles.body}>
        <span className={cx('text-title-card', styles.name)}>{name}</span>
        <span className={cx('text-body-s', styles.phone)}>{phone}</span>
      </span>
      <ChevronRightTinyIcon className={styles.chevron} />
    </button>
  );
}
