// 1:1 문의 화면 위쪽 '내 문의' 목록 (코드에서 정함 — 완결성 검토: '답변은 앱 알림으로' 라 약속했는데 답변을 볼 곳이 없었다)
// 쓰는 곳: Support.jsx(고객센터 화면)가 문의 입력칸 위에 넣는다. 데이터는 GET /support/inquiries (getMyInquiries).
// 문의가 하나도 없으면 아무것도 그리지 않는다(제목도 없음).
import { Card, ErrorState, Skeleton, cx, useAsync } from '@ansimclean/ui';
import { getMyInquiries } from '../../api/manager.js';
import styles from './profileSub.module.css';

// '내 문의' 목록 한 덩어리. 오류면 그 자리에 ErrorState(다시 시도), 받는 중이면 회색 자리 상자(Skeleton)
/** @param {{ refreshKey?: number }} props refreshKey 가 바뀌면 다시 받는다 (접수 직후) */
export default function MyInquiries({ refreshKey = 0 }) {
  const { data, error, reload } = useAsync(() => getMyInquiries(), [refreshKey]);
  if (error) return <ErrorState error={error} title="문의 내역을 불러오지 못했어요" onRetry={reload} />;
  if (!data) return <Skeleton height={72} />;
  if (data.length === 0) return null;
  return (
    <section className={styles.inquiries} aria-label="내 문의">
      <h2 className="text-title-card">내 문의</h2>
      {/* 답변 완료는 subtle, 대기는 muted 배경 카드. 답변(answer)이 있을 때만 아래에 구분선과 답변 칸 */}
      {data.map((q) => (
        <Card key={q.id} tone={q.status === 'answered' ? 'subtle' : 'muted'} padding="sm" gap="xs">
          <p className={cx('text-caption-l', styles.secondary)}>{q.createdLabel} · {q.status === 'answered' ? '답변 완료' : '답변 대기'}</p>
          <p className={cx('text-body-default', styles.preLine)}>{q.content}</p>
          {q.answer && (
            <div className={styles.answer}>
              <p className={cx('text-caption-l', styles.secondary)}>안심클린 답변 · {q.answeredLabel}</p>
              <p className={cx('text-body-default', styles.preLine)}>{q.answer}</p>
            </div>
          )}
        </Card>
      ))}
    </section>
  );
}
