// Figma(수리 예약): 안내 상자 — brand: brand/subtle 배경(방문 안내 1:450 "ⓘ", 배정 안내 1:268 "🔔", 선택 일정 요약 1:419 "📅")
//   warning: fff8e8 원본색(extra.css --raw-repair-notice-bg) 배경(안전 안내 1:351 "!", 비용 안내 1:321 제목+본문). 패딩 14(제목형은 16), radius sm, 글자 Caption/L secondary
// 공용 UI 의 안내 상자. 두 앱 화면이 '@ansimclean/ui' 에서 가져다 쓴다 (예: 고객 앱 RepairConfirm, 매니저 앱 JobDetail).
// 기호(glyph)·제목·본문을 세로(stack) 또는 가로(inline)로 놓는다.
import { cx } from '../../utils/cx.js';
import styles from './InfoNote.module.css';

/**
 * @param {object} props
 * @param {'brand'|'warning'} [props.tone='brand']
 * @param {string} [props.glyph] 앞에 붙는 기호 (Figma 는 텍스트: "ⓘ" "!" "🔔" "📅")
 * @param {'stack'|'inline'} [props.layout='stack'] stack=기호 위·본문 아래(간격 8), inline=기호와 본문 한 줄(간격 10, 선택 일정 요약)
 * @param {string} [props.title] 제목 (Title/Small) — 비용 안내
 * @param {import('react').ReactNode} props.children 본문
 * @param {string} [props.className]
 * 안내 상자 하나를 그린다. role="note" 로 스크린리더에 "본문을 돕는 보조 설명" 임을 알린다.
 */
export default function InfoNote({ tone = 'brand', glyph, layout = 'stack', title, children, className }) {
  // 클래스는 겹쳐 붙인다: 톤(배경색) + 배치(stack/inline) + 제목이 있으면 .titled(간격 8→6, 패딩 14→16).
  // 기호는 brand 톤이면서 제목이 없을 때만 브랜드색이고, 그 밖에는 글자색(text/primary)이다.
  // inline(선택 일정 요약)은 본문이 Label/Default·text/primary 로, stack 의 Caption/L·text/secondary 보다 진하다.
  return (
    <div className={cx(styles.note, styles[tone], styles[layout], title && styles.titled, className)} role="note">
      {glyph && <span className={cx('text-caption-l', styles.glyph, tone === 'brand' && !title && styles.glyphBrand)} aria-hidden="true">{glyph}</span>}
      {title && <p className={cx('text-title-small', styles.title)}>{title}</p>}
      <p className={cx(layout === 'inline' ? 'text-label-default' : 'text-caption-l', styles.body, layout === 'inline' && styles.bodyInline)}>{children}</p>
    </div>
  );
}
