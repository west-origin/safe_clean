// Figma: GiftBanner (2024:264) — 선물하기 프로모션 배너 393×160. 일러스트(144×132) 좌측, 헤드라인 + CTA 우측. 문구는 Figma 그대로.
// variant="repair": Repair 화면 인스턴스(2024:328) 오버라이드 — 일러스트(수리 선물)와 헤드라인 16 Regular/Bold(“곁에 없어도, 편리함은 선물할 수 있으니까.”), CTA "수리 선물하러 가기 >".
// 16 Regular 텍스트 스타일이 없어 Label/M(16 Medium) 클래스를 쓴다 (PROGRESS.md 판단 항목).
// 쓰는 곳 예: 고객 앱 Home(기본 variant), Repair(variant="repair"). 화면이 onCta 를 넘기지 않으면
// CTA 버튼 없이 일러스트와 헤드라인만 보인다 (이유는 아래 onCta 설명).
// 일러스트는 래스터(WebP) 1x/2x/3x 세 장을 import 해서 srcSet 으로 넘긴다. 화면 밀도에 맞는 파일은 브라우저가 고른다.
import gift1 from '../../assets/gift-illustration@1x.webp';
import gift2 from '../../assets/gift-illustration@2x.webp';
import gift3 from '../../assets/gift-illustration@3x.webp';
import giftRepair1 from '../../assets/gift-illustration-repair@1x.webp';
import giftRepair2 from '../../assets/gift-illustration-repair@2x.webp';
import giftRepair3 from '../../assets/gift-illustration-repair@3x.webp';
import { cx } from '../../utils/cx.js';
import styles from './GiftBanner.module.css';

/**
 * @param {object} props
 * @param {'home'|'repair'} [props.variant='home'] 문구 세트 (Home/* · Repair)
 * @param {() => void} [props.onCta] "청소 선물하러 가기 >" 를 눌렀을 때.
 *   **없으면 버튼을 그리지 않는다** — 선물하기 화면이 아직 없어 눌러도 아무 일이 없었다 (사용성 검토 2026-09-22).
 *   선물 플로우가 생기면 화면에서 onCta 를 넘기면 버튼이 다시 나온다.
 * @param {string} [props.className]
 */
export default function GiftBanner({ variant = 'home', onCta, className }) {
  const isRepair = variant === 'repair';
  return (
    <section className={cx(styles.banner, className)} aria-label={isRepair ? '수리 선물하기' : '청소 선물하기'}>
      {/* 일러스트는 꾸밈이라 alt="" (스크린리더가 건너뛴다). width/height 속성이 있어 이미지를 받기 전에도 그만큼 자리가 잡힌다 */}
      {isRepair ? (
        <img className={styles.illustration} src={giftRepair1} srcSet={`${giftRepair1} 1x, ${giftRepair2} 2x, ${giftRepair3} 3x`} width="144" height="132" alt="" />
      ) : (
        <img className={styles.illustration} src={gift1} srcSet={`${gift1} 1x, ${gift2} 2x, ${gift3} 3x`} width="144" height="132" alt="" />
      )}
      <div className={styles.content}>
        {/* 헤드라인은 한 문단 안에서 부분마다 글꼴이 달라 span 으로 나눠 각각 .text-* 클래스를 붙인다 (강조 단어는 strong) */}
        {isRepair ? (
          <p className={styles.headline}>
            <span className="text-label-m">“곁에 없어도, </span>
            <br />
            <span className="text-label-m">
              <strong className="text-title-card">편리함</strong>은 선물할 수 있으니까.”
            </span>
          </p>
        ) : (
          <p className={styles.headline}>
            <span className="text-body-l">“</span>
            <span className="text-body-m">깨끗함을 넘어</span>
            <span className="text-body-l">, </span>
            <br />
            <span className="text-body-l">
              <strong className="text-heading-s">안심</strong>까지 선물하세요.”
            </span>
          </p>
        )}
        {onCta && (
          <button type="button" className={cx('text-caption-l', styles.cta)} onClick={onCta}>
            {isRepair ? '수리 선물하러 가기 >' : '청소 선물하러 가기 >'}
          </button>
        )}
      </div>
    </section>
  );
}
