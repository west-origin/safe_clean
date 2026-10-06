// Figma: Auth/TermsConsent › PrivacyNoteCard (2063:1812) — 3D 아이콘(50×48) + 안내문(Body/S, 원본색 → --raw-note-text). 높이 80, 1px border/default, radius 20.
//        Auth/ProfileHousing › InfoBox (2064:1813) — tone="brand": brand/subtle 배경, 높이 64, 패딩 0/12/0/10, 간격 11, 안내문 Label/S text/secondary
//
// 왼쪽 3D 아이콘 + 오른쪽 안내문 한 덩어리의 안내 카드. 누를 수 없고 읽기만 한다.
// 예: 고객 앱 약관 동의(TermsConsent)는 기본 모양, 고객 앱 주거 정보 입력(ProfileHousing)은 tone="brand".
import { cx } from '../../utils/cx.js';
import styles from './NoteCard.module.css';

/**
 * @param {object} props
 * @param {{src: string, srcSet: string, width: number, height: number}} props.icon ASSETS 의 래스터 (예: ASSETS.termsNoteIcon)
 * @param {import('react').ReactNode} props.children 안내문 (Figma 문구 그대로. 줄바꿈은 \n)
 * @param {'default'|'brand'} [props.tone='default'] default=PrivacyNoteCard(흰 배경), brand=InfoBox(brand/subtle 배경)
 * @param {string} [props.className]
 */
export default function NoteCard({ icon, children, tone = 'default', className }) {
  // role="note" 는 "본문을 보충하는 안내" 라는 뜻의 접근성 역할이다. 아이콘은 장식이라 alt="" 로 읽지 않게 한다.
  // 글꼴은 tone 에 따라 다르다: brand(InfoBox)=Label/S, default(PrivacyNoteCard)=Body/S.
  return (
    <div className={cx(styles.card, styles[tone], className)} role="note">
      <img className={styles.icon} src={icon.src} srcSet={icon.srcSet} width={icon.width} height={icon.height} alt="" />
      <p className={cx(tone === 'brand' ? 'text-label-s' : 'text-body-s', styles.text)}>{children}</p>
    </div>
  );
}
