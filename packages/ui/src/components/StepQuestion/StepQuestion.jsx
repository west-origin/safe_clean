// Figma(수리 예약 rGCjx3zWMBk18fXgNZ9UYX): 질문 — 서비스 이름(Title/XS brand/primary) → 제목(Heading/M) → 안내(Body/S text/secondary), 간격 6
// 쓰는 곳: 고객 앱 수리 예약 단계 화면의 머리글(예: RepairAppliance·RepairConfirm).
// 문구는 화면이 넘긴다(서비스 이름은 repairFlow.js 의 serviceName·categoryLabel 로 만든다).
import { cx } from '../../utils/cx.js';
import styles from './StepQuestion.module.css';

/**
 * 예약 단계 화면의 머리글.
 * @param {object} props
 * @param {string} props.service 서비스 이름 (예: "가전수리 · 냉장고")
 * @param {string} props.title 질문 (예: "어떤 가전을 수리할까요?")
 * @param {string} [props.description] 안내 (Body/S)
 * @param {string} [props.className]
 */
// 서비스 이름 · 질문(h1) · 안내를 세로로 쌓는다. description 이 비어 있으면 안내 줄은 그리지 않는다.
export default function StepQuestion({ service, title, description, className }) {
  return (
    <header className={cx(styles.question, className)}>
      <p className={cx('text-title-xs', styles.service)}>{service}</p>
      <h1 className={cx('text-heading-m', styles.title)}>{title}</h1>
      {description && <p className={cx('text-body-s', styles.description)}>{description}</p>}
    </header>
  );
}
