// Figma: Manager/Home (2115:2222) · Manager/Home/Empty (2146:2988) — remaining 이 비면 Empty 상태로 조건부 렌더링
// 프로토타입: Button/Primary → Job/InProgress, Button/Secondary(콜 확인하기) → Call/List, 탭 → 각 탭 화면
// 코드에서 정함: 나머지 일정 카드 탭 → Job/Detail (수락한 작업을 당일 다시 열어 청소를 시작하는 경로)
// 매니저 앱 홈 탭 화면 (라우트 /). getHome(GET /manager/home) 응답 하나로 그리고, 아래 usePolling 이 조용히 다시 받는다.
// 위에서부터: 인사·오늘 예정 건수 → 콜 배정 정지 또는 승인 대기 안내 → 진행 중 작업 카드 → 새 콜 알림 카드 → 오늘 남은 일정.
// 진행 중 작업·새 콜·남은 일정이 모두 없으면 빈 상태(Figma Home/Empty)를 그린다.
import { useNavigate } from 'react-router';
import { ScreenLayout, ManagerTabBar, Card, Button, StatusBadge, ScheduleCard, Dot, EmptyState, cx, usePolling, useElapsed, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getHome } from '../../api/manager.js';
import { APPROVAL_PENDING_COPY, SUSPENDED_COPY } from '../../api/mock.js';
import { onPushReceived } from '../../push/pushClient.js';

const HOME_REFRESH_MS = 30_000; // 콜 목록과 같은 주기 — 홈에서 기다리는 매니저가 새 콜을 놓치지 않게 (외부 검토 3차)
import { useManagerTabs } from '../useManagerTabs.js';
import styles from './Home.module.css';

// 홈 화면 컴포넌트. 탭바 말고도 카드의 버튼·일정 카드를 누르면 진행 중·중단 화면, 콜 목록, 업무 상세, 근무 가능 시간으로 간다.
export default function Home() {
  const navigate = useNavigate();
  const goTab = useManagerTabs(); // 하단 탭을 누르면 그 탭 경로로 이동하는 함수
  // 30초마다·새 콜 푸시가 오면·앱이 다시 보이면 조용히 갱신 (실패는 다음 갱신이 메운다)
  const { data, error, reload } = usePolling(() => getHome(), [], { interval: HOME_REFRESH_MS, subscribe: onPushReceived });
  const elapsed = useElapsed(data?.inProgress?.startedAt, data?.inProgress?.pausedSeconds, data?.inProgress?.elapsed, data?.inProgress?.status === 'paused'); // 진행 중 카드 1초 타이머 (훅은 early return 위)


  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<ManagerTabBar active="home" onSelect={goTab} />} />;
  if (!data) return <ScreenLoading tabBar={<ManagerTabBar active="home" onSelect={goTab} />} />;
  const { manager, today, inProgress, newCalls, remaining } = data;
  const isEmpty = !inProgress && !newCalls && remaining.length === 0; // 셋 다 없을 때만 빈 상태 (상단 인사·안내 카드는 그대로 보인다)

  // 탭바의 콜 배지는 새 콜 알림(newCalls)이 있을 때만, 숫자는 newCalls.count
  return (
    <ScreenLayout tabBar={<ManagerTabBar active="home" onSelect={goTab} showCallBadge={Boolean(newCalls)} callBadgeCount={newCalls?.count} />}>
      <header className={styles.greeting}>
        <h1 className="text-heading-m">안녕하세요, {manager.name}님</h1>
        <p className={cx('text-body-default', styles.secondary)}>오늘도 안전하게 일해주세요.</p>
        <p className={cx('text-body-s', styles.secondary)}>
          {today.dateLabel} · 오늘 예정 {today.count}건
        </p>
      </header>

      {manager.suspension ? ( // 콜 배정 정지 — 승인된 매니저에게 '승인 대기' 를 보이지 않는다 (자체 점검 #11)
        <Card tone="warningMuted" gap="xs">
          <span className="text-title-card">{SUSPENDED_COPY.title}</span>
          <p className={cx('text-body-s', styles.secondary)}>{manager.suspension}</p>
        </Card>
      ) : manager.approved === false && ( // 아직 콜을 받을 수 없다 — 며칠을 기다려도 이유를 몰랐다 (검증 검토)
        <Card tone="warningMuted" gap="xs">
          <span className="text-title-card">{APPROVAL_PENDING_COPY.title}</span>
          <p className={cx('text-body-s', styles.secondary, styles.preLine)}>{APPROVAL_PENDING_COPY.description}</p>
        </Card>
      )}

      {isEmpty ? (
        <>
          <EmptyState icon="smile" title="오늘 예정된 업무가 없어요" description="지금은 쉬어가셔도 좋아요." />
          {/* Figma 프로토타입에 연결 없음 → 근무 가능 시간 화면으로 (코드에서 정함) */}
          <Button variant="outline" onClick={() => navigate('/schedule/availability')}>
            근무 일정 관리
          </Button>
        </>
      ) : (
        <>
          {/* 진행 중 작업 카드: 경과 시간은 useElapsed 가 1초마다 계산한다(일시정지면 멈춘 값).
              버튼은 일시정지 중이면 중단 화면(/job/paused), 아니면 진행 중 화면(/job/in-progress)으로 */}
          {inProgress && (
            <Card tone="live" gap="lg">
              <div className={styles.row}>
                <span className="text-heading-s">{inProgress.title}</span>
                <StatusBadge status={inProgress.status === 'paused' ? 'paused' : 'live'} />
              </div>
              <p className="text-heading-xl">{elapsed}</p>
              <p className={cx('text-body-s', styles.secondary)}>{inProgress.since}</p>
              <Button onClick={() => navigate(inProgress.status === 'paused' ? '/job/paused' : '/job/in-progress')}>업무 화면으로 돌아가기</Button>
            </Card>
          )}

          {/* 새 콜 알림 카드: 만료 문구(expiry, 가장 먼저 끝나는 콜 기준)는 서버 문구 그대로라
              콜 목록처럼 1초마다 줄지 않고 다시 받을 때 바뀐다 */}
          {newCalls && (
            <Card tone="brand" gap="md" className={styles.alert}>
              <div className={styles.row}>
                <span className={styles.rowStart}>
                  <Dot tone="live" />
                  <span className="text-title-card">새로운 청소 요청 {newCalls.count}건</span>
                </span>
                <span className={cx('text-title-xs', styles.warning)}>{newCalls.expiry}</span>
              </div>
              <p className={cx('text-body-s', styles.secondary)}>{newCalls.summary}</p>
              <Button variant="outline" onClick={() => navigate('/call')}>
                콜 확인하기
              </Button>
            </Card>
          )}

          {/* 오늘 남은 일정: 카드를 탭하면 그 작업의 업무 상세(/job/:id).
              서버는 id·jobId 를 둘 다 예약 id 로 준다. jobId 가 없으면 항목 id 로 연다 */}
          {remaining.length > 0 && (
            <section className={styles.section}>
              <h2 className="text-title-card">오늘의 나머지 일정</h2>
              {remaining.map((item) => (
                <ScheduleCard key={item.id} time={item.time} status={item.status} customer={item.customer} service={item.service} onClick={() => navigate(`/job/${item.jobId ?? item.id}`)} />
              ))}
            </section>
          )}
        </>
      )}
    </ScreenLayout>
  );
}
