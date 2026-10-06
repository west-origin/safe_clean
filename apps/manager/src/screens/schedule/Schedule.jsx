// Figma: Manager/Schedule (2124:2404) · Manager/Schedule/Empty (2146:2940) — items 가 비면 Empty 상태
// 프로토타입: 근무 일정 관리 → Schedule/Availability · 탭 → 각 탭 화면
// SegmentedControl 의 "7일"/"달력" 은 Figma 에 화면이 없음 → 7일은 오늘부터 7일치를 날짜별로 묶어 보여 주고, 달력은 Calendar 로 날을 골라 본다 (코드에서 정함)
// 라벨이 "이번 주" 였을 때는 다음 주 월요일 일정이 섞여 보였다(서버가 오늘~+6일을 준다) → "7일" 로 (사용성 검토 2026-09-22)
// 일정 카드 탭 → Job/Detail (수락한 작업을 다시 여는 경로, 코드에서 정함)
// 경로 /schedule (하단 탭 일정). 데이터 GET /manager/schedule — { items: 오늘, week: 오늘~+6일 날짜별 [{ dateKey, label, items }] }.
// week 에는 일정이 있는 날만 온다 (빈 날은 빠진다).
// 달력 탭에서 날을 고르면 그 달 전체를 ?from=&to= 로 한 번 더 받는다 (아래 monthQ).
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, ManagerTabBar, SegmentedControl, Button, ScheduleCard, EmptyState, Calendar, fromDateKey, toDateKey, usePolling, ScreenError, ScreenLoading, ErrorState, Skeleton } from '@ansimclean/ui';
import { getSchedule } from '../../api/manager.js';
import { onPushReceived } from '../../push/pushClient.js';
import { useManagerTabs } from '../useManagerTabs.js';
import styles from './Schedule.module.css';

// 화면에 머무는 동안 고객의 취소·일정 변경이 보이게 다시 받는 간격 — 홈·콜 목록과 같은 주기 (푸시가 오면 바로)
const SCHEDULE_REFRESH_MS = 30_000;

/** 기간 탭(SegmentedControl) 선택지. key 는 주소 ?period= 에 들어가는 값 */
const PERIODS = [
  { key: 'today', label: '오늘' },
  { key: 'week', label: '7일' },
  { key: 'calendar', label: '달력' },
];

/** 'YYYY-MM' → 그 달의 { from, to } (서버 GET /manager/schedule?from=&to=) */
function monthRange(monthKey) {
  const [y, m] = monthKey.split('-').map(Number);
  const first = new Date(y, m - 1, 1);
  const last = new Date(y, m, 0); // 다음 달의 0일 = 이 달의 마지막 날 (월은 0부터 세므로 m 이 다음 달)
  return { from: toDateKey(first), to: toDateKey(last) };
}

/** 일정 탭 화면. 오늘 / 7일 / 달력 세 보기, 카드를 누르면 업무 상세(/job/:id) */
export default function Schedule() {
  const navigate = useNavigate();
  const goTab = useManagerTabs();
  // 고객이 예약을 취소하거나 날짜를 바꾸면 서버가 매니저에게 푸시를 보낸다 — 화면이 열려 있어도 옛 카드가 남지 않게 조용히 다시 받는다
  const { data: data, error, reload } = usePolling(() => getSchedule(), [], { interval: SCHEDULE_REFRESH_MS, subscribe: onPushReceived });
  // 기간·고른 날은 URL(?period=&day=)에 — 업무 상세에서 돌아와도 그대로 (검증 검토: useState 라 '오늘' 탭으로 초기화됐다)
  const [params, setParams] = useSearchParams();
  const period = PERIODS.some((p) => p.key === params.get('period')) ? params.get('period') : 'today'; // 없거나 모르는 값이면 '오늘'
  const dayKey = params.get('day') || null; // 달력에서 고른 날 'YYYY-MM-DD' (없으면 null)
  // 두 setter 는 다른 쿼리 값은 두고 자기 값만 바꾼다. replace: 탭·날짜를 바꿀 때마다 방문 기록이 쌓이지 않게 지금 칸을 바꿔 쓴다
  const setPeriod = (next) => setParams((prev) => {
    const q = new URLSearchParams(prev);
    q.set('period', next);
    return q;
  }, { replace: true });
  const setDayKey = (next) => setParams((prev) => {
    const q = new URLSearchParams(prev);
    if (next) q.set('day', next);
    else q.delete('day');
    return q;
  }, { replace: true });
  // 달력 탭: 기본 응답은 오늘~+6일뿐이라 고른 날의 달 전체를 따로 받는다 (없으면 7일 밖 일정이 전부 '없음' 으로 보였다)
  const monthKey = dayKey ? dayKey.slice(0, 7) : null;
  // 달력 탭을 보고 있을 때만 다시 받는다 — 다른 탭에서는 쓰이지 않는데 30초마다·푸시마다 한 번 더 받았다
  const calendarOpen = period === 'calendar';
  const monthQ = usePolling(() => (monthKey ? getSchedule(monthRange(monthKey)) : null), [monthKey], { interval: calendarOpen ? SCHEDULE_REFRESH_MS : 0, subscribe: calendarOpen ? onPushReceived : undefined }); // 고른 날이 없으면 요청하지 않는다(null). 달이 바뀔 때만 다시 받는다


  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<ManagerTabBar active="schedule" onSelect={goTab} />} />;
  if (!data) return <ScreenLoading tabBar={<ManagerTabBar active="schedule" onSelect={goTab} />} />;

  return (
    <ScreenLayout gap="sm" tabBar={<ManagerTabBar active="schedule" onSelect={goTab} />}>
      <h1 className="text-heading-s">일정</h1>
      <SegmentedControl options={PERIODS} value={period} onChange={setPeriod} ariaLabel="기간" />
      <Button variant="secondary" onClick={() => navigate('/schedule/availability')}>
        근무 일정 관리
      </Button>

      {period === 'today' && (data.items.length === 0 ? (
        <EmptyState icon="calendar" title="오늘 예정된 청소가 없어요" description="쉬는 날이에요. 콜이 들어오면 알려드릴게요." />
      ) : (
        <div className={styles.list}>
          {/* 카드 → 업무 상세. 서버는 jobId 를 준다(지금은 id 와 같은 값). jobId 가 없는 목 데이터는 id 로 */}
          {data.items.map((item) => (
            <ScheduleCard key={item.id} time={item.time} status={item.status} customer={item.customer} service={item.service} onClick={() => navigate(`/job/${item.jobId ?? item.id}`)} />
          ))}
        </div>
      ))}

      {period === 'week' && (data.week.length === 0 ? (
        <EmptyState icon="calendar" title="앞으로 7일 안에 예정된 청소가 없어요" />
      ) : (
        data.week.map((day) => (
          <section key={day.dateKey} className={styles.list} aria-label={day.label}>
            <h2 className="text-title-card">{day.label}</h2>
            {day.items.map((item) => (
              <ScheduleCard key={item.id} time={item.time} status={item.status} customer={item.customer} service={item.service} onClick={() => navigate(`/job/${item.jobId ?? item.id}`)} />
            ))}
          </section>
        ))
      ))}

      {/* 달력 보기: 그 자리에서 바로 실행하는 함수로, 고른 날과 그날 일정을 계산한 뒤 그린다 */}
      {period === 'calendar' && (() => {
        const selected = dayKey ?? data.week[0]?.dateKey; // 고른 날이 없으면 7일 안에서 일정이 있는 첫 날 (7일 모두 비면 선택 없음)
        const source = monthKey && monthQ.data ? monthQ.data.week : data.week; // 그 달 응답이 왔으면 그것에서, 아니면 기본 7일 응답에서 찾는다
        const day = source.find((d) => d.dateKey === selected);
        // Calendar 는 기본으로 지난 날·주말을 막는다(예약용) — 일정 보기는 모든 날을 고를 수 있게 isDisabled 가 늘 false
        return (
          <>
            <Calendar title="날짜 선택" value={selected} onChange={setDayKey} isDisabled={() => false} initialMonth={fromDateKey(selected) ?? undefined} />
            {monthQ.error ? (
              <ErrorState title="일정을 불러오지 못했어요" onRetry={monthQ.reload} />
            ) : monthKey && monthQ.loading ? (
              // 화면 안의 부분 로딩 — ScreenLoading 은 ScreenLayout(main·100dvh)을 통째로 그려 화면 안에 겹치면 한 화면 높이가 더 생긴다 (검수 4차)
              <Skeleton height={96} radius="md" />
            ) : day && day.items.length > 0 ? (
              <div className={styles.list}>
                {day.items.map((item) => (
                  <ScheduleCard key={item.id} time={item.time} status={item.status} customer={item.customer} service={item.service} onClick={() => navigate(`/job/${item.jobId ?? item.id}`)} />
                ))}
              </div>
            ) : (
              <EmptyState icon="calendar" title="이 날은 예정된 청소가 없어요" />
            )}
          </>
        );
      })()}
    </ScreenLayout>
  );
}
