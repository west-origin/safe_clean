// 클래스명 합치기. 거짓값(false/null/undefined/'')은 건너뛴다.
// 예: cx(styles.card, active && styles.active, className)
// 공용 UI 컴포넌트와 두 앱 화면이 쓴다 (packages/ui/src/index.js 에서 다시 내보낸다).
export const cx = (...names) => names.filter(Boolean).join(' ');
