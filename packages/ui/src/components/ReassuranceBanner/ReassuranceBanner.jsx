// Figma: Service/LivePortrait › ReassuranceBanner (2167:2299) — 345×72, brand/subtle, radius 20, 패딩 14/16, 간격 12. Mascot 44 원(일러스트 에셋 필요 → --raw-mascot-placeholder 자리표시) + 제목 Title/XS + 부제 Body/XS
// TODO(asset): 에셋 필요 — Mascot (2167:2300) 마스코트 일러스트 44×44
// 라이브 화면 아래쪽의 안심 문구 카드(마스코트 + 제목 + 부제). 고객 앱 ServiceLivePortrait 가
// useLiveSession 이 준 session.reassurance 의 제목·설명을 넣어 부른다. 그 화면은 mascot 을 넘기지 않아 자리표시 원이 보인다.
import { cx } from '../../utils/cx.js';
import styles from './ReassuranceBanner.module.css';

/**
 * @param {object} props
 * @param {string} props.title (Figma "안심하고 지켜보세요!")
 * @param {string} props.description (Figma "항상 최선을 다해 청소하고 있어요 💙")
 * @param {{src: string, srcSet: string}} [props.mascot] 마스코트 이미지. 없으면 자리표시 원
 * @param {string} [props.className]
 * role="note" 로 본문에 딸린 안내임을 보조기기에 알린다. 마스코트는 장식이라 alt="" / aria-hidden 이다.
 */
export default function ReassuranceBanner({ title, description, mascot, className }) {
  // 이미지가 있으면 <img>, 없으면 같은 크기의 빈 원(.mascot 배경색)으로 자리만 잡는다
  return (
    <div className={cx(styles.banner, className)} role="note">
      {mascot ? <img className={styles.mascot} src={mascot.src} srcSet={mascot.srcSet} width="44" height="44" alt="" /> : <span className={styles.mascot} aria-hidden="true" />}
      <span className={styles.text}>
        <span className={cx('text-title-xs', styles.title)}>{title}</span>
        <span className={cx('text-body-xs', styles.description)}>{description}</span>
      </span>
    </div>
  );
}
