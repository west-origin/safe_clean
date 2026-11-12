// (코드에서 정함) 요금·견적 안내 — 2026-11-08 사용자 첨부 시안 5장(가정 정기청소·원룸 정기청소·후드·덕트 분해청소·대리석 코팅·크랙 수리)의 공통 틀.
// 문구·금액은 화면이 guide 객체로 넘긴다 (고객 앱 api/mock.js PRICE_GUIDES). 데이터에 있는 부분만 그린다:
//   머리(아이콘·eyebrow·제목·설명) → 기본 요금 띠(base) → 횟수별 요금표(plans) → 카드 두 장(cards) → 크기별 요금(sizes) → 할인 상자(discount) → 추가 요금(extras) → 계산 예시(examples) → 알아 두세요(notes) → 혜택 띠(benefits) → 청소 범위(scope) → 각주
// 쓰는 곳: 고객 앱 BookingFrequency(기본 청소) 화면 아래, Repair(수리 탭) 카테고리 아래.
import { useId } from 'react';
import { cx } from '../../utils/cx.js';
import ServiceHomeIcon from '../../icons/ServiceHomeIcon.jsx';
import ServiceStudioIcon from '../../icons/ServiceStudioIcon.jsx';
import ServiceDisassemblyIcon from '../../icons/ServiceDisassemblyIcon.jsx';
import ServiceRepairIcon from '../../icons/ServiceRepairIcon.jsx';
import ServicePhotoIcon from '../../icons/ServicePhotoIcon.jsx';
import HoodSizeIcon from '../../icons/HoodSizeIcon.jsx';
import GiftBoxIcon from '../../icons/GiftBoxIcon.jsx';
import CountertopIcon from '../../icons/CountertopIcon.jsx';
import CrackIcon from '../../icons/CrackIcon.jsx';
import styles from './PriceGuide.module.css';

const HEAD_ICONS = { house: ServiceHomeIcon, studio: ServiceStudioIcon, disassembly: ServiceDisassemblyIcon, repair: ServiceRepairIcon };
const BENEFIT_ICONS = { gift: GiftBoxIcon, camera: ServicePhotoIcon };

/** 329000 → "329,000" */
const comma = (n) => n.toLocaleString('ko-KR');

/**
 * @typedef {object} Guide
 * @property {'house'|'studio'|'disassembly'|'repair'} icon 머리 아이콘
 * @property {string} eyebrow 파란 윗줄 (예: "요금 안내")
 * @property {string} title 제목
 * @property {string} subtitle 회색 설명
 * @property {{title:string, sub:string, price?:number, badge?:string}[]} [plans] 횟수별 월 요금. price 가 없으면 '상담'
 * @property {{tone:'default'|'brand', pill:string, title:string, amount?:number, note?:string, highlight?:string}[]} [cards] 아래 카드 두 장
 * @property {string} [sizesTitle] 크기별 요금 제목
 * @property {{size?:'s'|'m'|'l', shape?:'i'|'l'|'u', title:string, sub:string, price:number}[]} [sizes] size = 후드 그림, shape = 상판 그림
 * @property {{caption:string, title:string, sub?:string, price:number}} [base] 파란 기본 요금 띠
 * @property {{badge:string, text:string, strong:string, items:{label:string, price:number}[]}} [discount] 할인 상자
 * @property {{title:string, legend:string[], items:{crack:'long'|'branch'|'apart'|'branch2', title:string, formula:string, added:string, price:number}[]}} [examples] 계산 예시
 * @property {{title:string, rows:{label:string, value:string}[]}} [notes] 알아 두세요
 * @property {{title:string, aside:string, rows:{label:string, hint?:string, price:number}[]}} [extras] 추가 요금 (hint = 회색 괄호 글)
 * @property {{icon:'gift'|'camera', caption:string, title:string}[]} [benefits] 혜택 띠
 * @property {string} [scopeTitle] 청소 범위 제목
 * @property {string[]} [scope] 청소 범위 알약
 * @property {string} [footnote] 맨 아래 각주 ("* " 는 컴포넌트가 붙인다)
 */

/**
 * @param {object} props
 * @param {Guide} props.guide 안내 내용
 * @param {string} [props.className] 바깥 section 에 더할 클래스 (화면 여백)
 */
export default function PriceGuide({ guide, className }) {
  const HeadIcon = HEAD_ICONS[guide.icon];
  const titleId = useId(); // 한 화면에 안내가 여럿이어도 제목 id 가 겹치지 않게
  return (
    <section className={cx(styles.guide, className)} aria-labelledby={titleId}>
      <header className={styles.head}>
        <span className={styles.headIcon}>{HeadIcon && <HeadIcon size={56} />}</span>
        <div className={styles.headText}>
          <p className={cx('text-title-xs', styles.eyebrow)}>{guide.eyebrow}</p>
          <h2 id={titleId} className={cx('text-heading-m', styles.ink)}>{guide.title}</h2>
          <p className={cx('text-body-s', styles.muted)}>{guide.subtitle}</p>
        </div>
      </header>

      {guide.base && (
        <div className={styles.base}>
          <div className={styles.baseText}>
            <span className="text-body-xs">{guide.base.caption}</span>
            <span>
              <strong className="text-title-card">{guide.base.title}</strong>{' '}
              {guide.base.sub && <span className="text-caption">{guide.base.sub}</span>}
            </span>
          </div>
          <span className={styles.amount}>
            <strong className="text-heading-s">{comma(guide.base.price)}</strong>
            <span className="text-caption-bold">원</span>
          </span>
        </div>
      )}

      {guide.plans && (
        <ul className={cx(styles.card, styles.plans)}>
          {guide.plans.map((p) => (
            <li key={p.title} className={styles.planRow}>
              <div className={styles.planText}>
                <span className={styles.planTitle}>
                  <strong className={cx('text-heading-s', styles.ink)}>{p.title}</strong>
                  {p.badge && <span className={cx('text-caption-bold', styles.badge)}>{p.badge}</span>}
                </span>
                <span className={cx('text-body-s', styles.muted)}>{p.sub}</span>
              </div>
              {p.price ? (
                <span className={styles.amount}>
                  <strong className={cx('text-heading-s', styles.ink)}>{comma(p.price)}</strong>
                  <span className={cx('text-caption-bold', styles.ink)}>원</span>
                  <span className={cx('text-caption', styles.muted)}>/월</span>
                </span>
              ) : (
                <strong className={cx('text-heading-s', styles.brand)}>상담</strong>
              )}
            </li>
          ))}
        </ul>
      )}

      {guide.cards && (
        <div className={styles.cards}>
          {guide.cards.map((c) => (
            <div key={c.pill} className={cx(styles.card, styles.infoCard, c.tone === 'brand' && styles.brandCard)}>
              <span className={cx('text-caption-bold', c.tone === 'brand' ? styles.badge : styles.softBadge)}>{c.pill}</span>
              <strong className={cx('text-title-card', styles.ink)}>{c.title}</strong>
              {c.amount && (
                <span className={styles.amount}>
                  <strong className={cx('text-heading-s', styles.ink)}>{comma(c.amount)}</strong>
                  <span className={cx('text-caption-bold', styles.ink)}>원</span>
                </span>
              )}
              {c.highlight && <span className={cx('text-body-bold', styles.brand)}>{c.highlight}</span>}
              {c.note && <span className={cx('text-body-xs', styles.muted)}>{c.note}</span>}
            </div>
          ))}
        </div>
      )}

      {guide.sizes && (
        <div className={styles.block}>
          <h3 className={cx('text-heading-s', styles.ink)}>{guide.sizesTitle}</h3>
          <ul className={styles.sizes}>
            {guide.sizes.map((s) => (
              <li key={s.title} className={cx(styles.card, styles.sizeCard)}>
                {s.shape ? <CountertopIcon shape={s.shape} className={styles.sizeArt} /> : <HoodSizeIcon size={s.size} className={styles.sizeArt} />}
                <strong className={cx('text-title-card', styles.ink)}>{s.title}</strong>
                <span className={cx('text-body-xs', styles.muted)}>{s.sub}</span>
                <span className={styles.amount}>
                  <strong className={cx('text-title-card', styles.ink)}>{comma(s.price)}</strong>
                  <span className={cx('text-caption-bold', styles.ink)}>원</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {guide.discount && (
        <div className={styles.discount}>
          <p className={styles.discountHead}>
            <span className={cx('text-caption-bold', styles.badge)}>{guide.discount.badge}</span>
            <span className={cx('text-body-xs', styles.muted)}>
              {guide.discount.text} <strong className={styles.ink}>{guide.discount.strong}</strong>
            </span>
          </p>
          <ul className={styles.discountItems}>
            {guide.discount.items.map((d) => (
              <li key={d.label} className={styles.discountItem}>
                <span className={cx('text-caption', styles.muted)}>{d.label}</span>
                <span className={styles.amount}>
                  <strong className={cx('text-body-bold', styles.brand)}>{comma(d.price)}</strong>
                  <span className={cx('text-caption-bold', styles.brand)}>원</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {guide.extras && (
        <div className={cx(styles.card, styles.extras)}>
          <div className={styles.extrasHead}>
            <h3 className={cx('text-title-card', styles.ink)}>{guide.extras.title}</h3>
            <span className={cx('text-caption', styles.muted)}>{guide.extras.aside}</span>
          </div>
          <ul className={styles.extraRows}>
            {guide.extras.rows.map((r) => (
              <li key={r.label} className={styles.extraRow}>
                <span className={cx('text-body-bold', styles.ink)}>
                  {r.label}
                  {r.hint && <span className={cx('text-caption', styles.muted)}> ({r.hint})</span>}
                </span>
                <span className={styles.amount}>
                  <strong className={cx('text-title-card', styles.brand)}>+{comma(r.price)}</strong>
                  <span className={cx('text-caption-bold', styles.brand)}>원</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {guide.examples && (
        <div className={styles.block}>
          <div className={styles.extrasHeadPlain}>
            <h3 className={cx('text-heading-s', styles.ink)}>{guide.examples.title}</h3>
            <span className={styles.legend}>
              <span className={cx('text-caption', styles.muted, styles.legendBase)}>{guide.examples.legend[0]}</span>
              <span className={cx('text-caption', styles.muted, styles.legendAdd)}>{guide.examples.legend[1]}</span>
            </span>
          </div>
          <ul className={styles.examples}>
            {guide.examples.items.map((e) => (
              <li key={e.title} className={cx(styles.card, styles.example)}>
                <CrackIcon type={e.crack} className={styles.exampleArt} />
                <div className={styles.planText}>
                  <strong className={cx('text-body-bold', styles.ink)}>{e.title}</strong>
                  <span className="text-caption">
                    <span className={styles.ink}>{e.formula} + </span>
                    <span className={styles.brand}>{e.added}</span>
                  </span>
                  <span className={styles.amount}>
                    <strong className={cx('text-title-card', styles.ink)}>{comma(e.price)}</strong>
                    <span className={cx('text-caption-bold', styles.ink)}>원</span>
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {guide.notes && (
        <div className={cx(styles.card, styles.extras)}>
          <h3 className={cx('text-title-card', styles.ink, styles.notesHead)}>{guide.notes.title}</h3>
          <ul>
            {guide.notes.rows.map((r) => (
              <li key={r.label} className={styles.extraRow}>
                <span className={cx('text-body-bold', styles.ink)}>{r.label}</span>
                <span className={cx('text-body-xs', styles.muted)}>{r.value}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {guide.benefits?.map((b) => {
        const Icon = BENEFIT_ICONS[b.icon];
        return (
          <div key={b.title} className={styles.benefit}>
            {Icon && <Icon size={40} />}
            <div className={styles.benefitText}>
              <span className={cx('text-body-xs', styles.muted)}>{b.caption}</span>
              <strong className={cx('text-title-card', styles.ink)}>{b.title}</strong>
            </div>
          </div>
        );
      })}

      {guide.scope && (
        <div className={styles.block}>
          <h3 className={cx('text-heading-s', styles.ink)}>{guide.scopeTitle}</h3>
          <ul className={styles.scope}>
            {guide.scope.map((s) => (
              <li key={s} className={cx('text-body-bold', styles.scopeChip)}>{s}</li>
            ))}
          </ul>
        </div>
      )}

      {guide.footnote && <p className={cx('text-body-xs', styles.muted)}>* {guide.footnote}</p>}
    </section>
  );
}
