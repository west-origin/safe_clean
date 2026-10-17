// PDF 6-7쪽: 기존 최신 라이브 화면에 ProgressOverlay 카드(6003:2260)만 추가한다.
import { cx } from '../../utils/cx.js';
import ProgressBar from '../ProgressBar/index.js';
import ChecklistRequests from '../ChecklistRequests/index.js';
import styles from './ChecklistProgressCard.module.css';

/**
 * @param {object} props
 * @param {{done:number,total:number,percent:number,groups:{group:string,done:number,total:number,items:{id:string,label:string,done:boolean}[]}[],requests?:string[]}} props.checklist
 * @param {{title:string,basis:string,requestsTitle:string,requestsStatus:string,requestsEmpty:string,requestsNote:string}} props.copy
 * @param {string} [props.className]
 */
export default function ChecklistProgressCard({ checklist, copy, className }) {
  const { done, total, percent, groups, requests = [] } = checklist;
  return (
    <section className={cx(styles.card, className)} aria-label={copy.title}>
      <div className={styles.head}>
        <h2 className={cx('text-body-bold', styles.primary)}>{copy.title}</h2>
        <span className={cx('text-caption-l-bold', styles.brand)}>{total}개 중 {done}개 완료</span>
      </div>
      <ProgressBar value={done} max={total} label={copy.title} className={styles.bar} />
      <p className={cx('text-caption', styles.secondary)}>{copy.basis} · {percent}%</p>
      <div className={styles.groups}>
        {groups.map((group) => (
          <div key={group.group} className={styles.group}>
            <h3 className={cx('text-caption-l-bold', styles.primary)}>{group.group} {group.done}/{group.total}</h3>
            <ul className={styles.items}>
              {group.items.map((item) => (
                <li key={item.id} className={cx('text-caption-l', item.done ? styles.brand : styles.secondary)} aria-label={`${item.label} ${item.done ? '완료' : '미완료'}`}>
                  {item.done ? '✓' : '○'} {item.label}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <hr className={styles.divider} />
      <ChecklistRequests copy={copy} requests={requests} />
    </section>
  );
}
