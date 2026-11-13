// PersonRow — 사람 한 명 줄: 이름표(닉네임#태그) + 보조 문구(가린 이름 등) + 오른쪽 작은 버튼들.
// Figma 에 없어 코드에서 정함 (2026-11-13 고객 친구 화면 — PROGRESS.md 판단 항목). 쓰는 곳: 고객 앱 친구 목록·받은/보낸 요청·차단 목록·검색 결과.
// 버튼은 tone 으로 두 가지: primary(채운 파랑 — 수락·요청) / neutral(회색 테두리 — 거절·취소·삭제). 시각 높이 36 이지만 터치 영역은 44.
import { cx } from '../../utils/cx.js';
import styles from './PersonRow.module.css';

/**
 * @param {object} props
 * @param {string} props.title 굵은 첫 줄 (예: 친구 코드 "청소왕#0420" — token-ok: 색이 아니다)
 * @param {string} [props.subtitle] 둘째 줄 (예: "김*수 · 11월 13일")
 * @param {{ key?: string, label: string, onClick: () => void, tone?: 'primary'|'neutral', disabled?: boolean, ariaLabel?: string }[]} [props.actions]
 *   오른쪽 버튼들. disabled 는 진행 중(요청 보내는 중) 표시에도 쓴다. ariaLabel 이 없으면 "라벨 + 제목" 으로 읽힌다
 * @param {string} [props.className]
 */
export default function PersonRow({ title, subtitle, actions = [], className }) {
  return (
    <div className={cx(styles.row, className)}>
      <div className={styles.text}>
        <p className={cx('text-body-bold', styles.title)}>{title}</p>
        {subtitle && <p className={cx('text-body-s', styles.subtitle)}>{subtitle}</p>}
      </div>
      {actions.length > 0 && (
        <div className={styles.actions}>
          {actions.map((a) => (
            <button
              key={a.key ?? a.label}
              type="button"
              className={cx('text-label-s', styles.action, a.tone === 'primary' ? styles.primary : styles.neutral)}
              disabled={a.disabled}
              aria-label={a.ariaLabel ?? `${title} ${a.label}`}
              onClick={a.onClick}
            >
              {a.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
