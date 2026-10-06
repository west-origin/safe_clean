// Figma: Manager/CameraStatusItem › StatusIcon/Issue (2147:3002). path 는 Figma 내보낸 원본 그대로, 색은 currentColor.
// 모양: 삼각형 안 느낌표. 쓰는 곳(예): CameraStatusItem — status 가 "ok" 가 아니면(예: "issue") 이것을 그린다. "ok" 면 StatusOkIcon.

/**
 * @param {object} props
 * @param {number} [props.size=18] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function StatusIssueIcon({ size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 18 18" fill="none" aria-hidden="true" focusable="false" className={className}>
      <path d="M9 6.75V10.125M9 12.9H9.0075M7.725 2.925L1.35 13.5C1.28298 13.6159 1.24821 13.7476 1.24929 13.8815C1.25037 14.0154 1.28726 14.1465 1.35614 14.2613C1.42502 14.3761 1.52338 14.4704 1.64099 14.5343C1.75861 14.5983 1.8912 14.6296 2.025 14.625H15.975C16.1088 14.6296 16.2414 14.5983 16.359 14.5343C16.4766 14.4704 16.575 14.3761 16.6439 14.2613C16.7127 14.1465 16.7496 14.0154 16.7507 13.8815C16.7518 13.7476 16.717 13.6159 16.65 13.5L10.275 2.925C10.2077 2.81647 10.1139 2.7269 10.0023 2.6648C9.89076 2.60269 9.76519 2.57009 9.6375 2.57009C9.50982 2.57009 9.38424 2.60269 9.27268 2.6648C9.16112 2.7269 9.06726 2.81647 9 2.925H7.725Z" stroke="currentColor" strokeWidth={1.65} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
