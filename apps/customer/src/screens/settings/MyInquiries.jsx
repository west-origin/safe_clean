// 1:1 문의 화면 위쪽 '내 문의' 목록 (코드에서 정함 — 완결성 검토: '답변은 앱 알림으로' 라 약속했는데 답변을 볼 곳이 없었다)
// 쓰는 곳 예: 1:1 문의 화면(Inquiry.jsx) 위쪽. 서버 GET /support/inquiries 에서 내 문의와 답변을 받는다.
import { Card, ErrorState, Skeleton, cx, useAsync } from '@ansimclean/ui';
import { getMyInquiries } from '../../api/customer.js';
import styles from './settingsSub.module.css';

/** @param {{ refreshKey?: number }} props refreshKey 가 바뀌면 다시 받는다 (접수 직후) */
export default function MyInquiries({ refreshKey = 0 }) {
  const { data, error, reload } = useAsync(() => getMyInquiries(), [refreshKey]);
  // ScreenError 는 화면 전체 틀(ScreenLayout)을 그리므로, 화면 일부인 여기서는 ErrorState·Skeleton 만 이 자리에 그린다.
  // 문의가 없으면 목록 자체를 그리지 않는다
  if (error) return <ErrorState error={error} title="문의 내역을 불러오지 못했어요" onRetry={reload} />;
  if (!data) return <Skeleton height={72} />;
  if (data.length === 0) return null;
  // <section aria-label> 은 이름이 있는 영역(region)이 된다 — 테스트가 이 이름으로 목록을 찾는다.
  // 카드 색: 답변 완료는 subtle, 답변 대기는 muted. 답변이 있을 때만 구분선 아래에 답변을 붙인다
  return (
    <section className={styles.inquiries} aria-label="내 문의">
      <h2 className="text-title-card">내 문의</h2>
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
