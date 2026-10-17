// PDF: ProgressOverlay 6003:2260의 고객 요청사항. 요청의 완료 데이터는 없어 항상 미확인으로 표시한다.
import { cx } from '../../utils/cx.js';
import styles from './ChecklistRequests.module.css';

/**
 * @param {object} props
 * @param {{requestsTitle:string,requestsStatus:string,requestsEmpty:string,requestsNote:string}} props.copy
 * @param {string[]} [props.requests] 요청이 없으면 문서의 빈 문구를 표시한다.
 */
export default function ChecklistRequests({ copy, requests = [] }) {
  return (
    <div className={styles.requests}>
      <div className={styles.head}>
        <h3 className={cx('text-title-small', styles.primary)}>{copy.requestsTitle}</h3>
        <span className={cx('text-caption', styles.secondary)}>{copy.requestsStatus}</span>
      </div>
      {requests.length ? (
        <ul className={styles.list}>
          {requests.map((request, index) => <li key={`${index}-${request}`} className={cx('text-caption-l', styles.secondary)}>• {request}</li>)}
        </ul>
      ) : <p className={cx('text-caption-l', styles.secondary)}>{copy.requestsEmpty}</p>}
      <p className={cx('text-caption', styles.secondary)}>{copy.requestsNote}</p>
    </div>
  );
}
