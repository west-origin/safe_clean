// Figma: Manager/CameraStatusItem (2147:3005) — Status=OK|Issue + Label, Value TEXT. 상하 14, 간격 6.
// 촬영 장비(바디캠) 상태 한 줄: 왼쪽 항목명, 오른쪽 아이콘 + 상태 문구.
// 예: 매니저 앱 작업 준비(JobPreparing)·장비(Equipment) 화면이 api 로 받은 목록을 한 줄씩 그린다.
// 줄 사이 구분선은 감싸는 Card(divided)가 그린다.
import StatusOkIcon from '../../icons/StatusOkIcon.jsx';
import StatusIssueIcon from '../../icons/StatusIssueIcon.jsx';
import { cx } from '../../utils/cx.js';
import styles from './CameraStatusItem.module.css';

/**
 * @param {object} props
 * @param {'ok'|'issue'} props.status
 * @param {string} props.label 왼쪽 항목명 (Figma "Label", 예: 카메라)
 * @param {string} props.value 오른쪽 상태 문구 (Figma "Value", 예: 정상 / 연결 안 됨)
 * @param {string} [props.className]
 */
export default function CameraStatusItem({ status, label, value, className }) {
  const isOk = status === 'ok'; // 'ok' 가 아니면 모두 문제(issue) 모양으로 그린다
  // role="status": 화면 낭독기에 상태 영역임을 알린다. 상태를 문구(value)로도 보여 색만으로 구분하지 않는다
  return (
    <div className={cx(styles.item, isOk ? styles.ok : styles.issue, className)} role="status">
      <span className={cx('text-body-default', styles.label)}>{label}</span>
      <span className={styles.value}>
        {isOk ? <StatusOkIcon /> : <StatusIssueIcon />}
        <span className="text-title-small">{value}</span>
      </span>
    </div>
  );
}
