// Figma: Auth/TermsConsent › TermRow (2063:1807) — Checkbox + 약관 라벨(Body/S) + "보기>" 링크(Caption, text/secondary)
// 약관 한 줄. 쓰는 곳: 고객 앱 TermsConsent(보기 → /terms/:id), 매니저 앱 SignUpTerms(보기 → /auth/terms/:id).
// 체크 상태는 부모가 가진다(제어 컴포넌트): checked 를 내려 주고, 누르면 onChange(새 체크값) 만 부른다.
import Checkbox from '../Checkbox/Checkbox.jsx';
import { cx } from '../../utils/cx.js';
import styles from './TermRow.module.css';

/**
 * @param {object} props
 * @param {string} props.label 약관 문구 (Figma 문구 그대로, 예: "(필수) 서비스 이용약관")
 * @param {boolean} props.checked
 * @param {(checked: boolean) => void} [props.onChange]
 * @param {() => void} [props.onView] "보기>" 탭. Figma 에 연결된 화면이 없어 화면이 정한다
 * @param {string} [props.viewLabel='보기>'] (Figma "ViewLink" 문구)
 * @param {string} [props.className]
 */
// 왼쪽 체크박스(라벨 포함) + 오른쪽 "보기>" 버튼. 두 동작은 서로 따로다(보기를 눌러도 체크는 바뀌지 않는다).
export default function TermRow({ label, checked, onChange, onView, viewLabel = '보기>', className }) {
  return (
    <div className={cx(styles.row, className)}>
      <Checkbox label={label} checked={checked} onChange={onChange} className={styles.checkbox} />
      {/* 약관마다 "보기>" 가 있어 aria-label 에 약관 문구를 붙여 스크린리더가 어느 약관인지 구분하게 한다 */}
      <button type="button" className={cx('text-caption', styles.view)} onClick={onView} aria-label={`${label} 보기`}>
        {viewLabel}
      </button>
    </div>
  );
}
