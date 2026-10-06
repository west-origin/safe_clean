// Figma: Reservations (2047:1683) — 헤더 + 밑줄 탭(예정 N / 완료 / 취소) + ReservationCard 목록 + TabBar(Reservations)
// Figma 에 없어 코드에서 정한 것: 완료·취소 탭 목록과 빈 상태, 예약 변경(→ 날짜/시간 화면 ?edit=), 예약 취소(확인 시트), 진행 중 카드의 "실시간 영상 보기"(소개 화면 Service/Start 를 거치지 않고 바로 /service/live)
// (코드에서 정함, B3) 정기청소 회차(series)의 취소 시트에는 "이번 회차만 / 정기청소 전체" 선택이 있다 (BACKEND_PLAN.md §9.2-5). 서버가 기한 규칙으로 거부하면 그 문구를 보여준다
// 고객 앱 '내 예약' 탭 화면 (/reservations). 데이터는 getReservations(GET /customer/reservations) 한 번으로
// 헤더 문구·탭 목록·예약 목록을 받는다. 각 예약의 tab(어느 탭에 속하는지)·status·rows(카드 행 문구)는 서버가 정해 준다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, TabBar, UnderlineTabs, ReservationCard, EmptyState, Sheet, Button, RadioOption, ScreenError, ScreenLoading, usePolling, useAsync, useAction, cx, ActionError, showToast } from '@ansimclean/ui';
import { getReservations, cancelReservation, getSupportPhone } from '../../api/customer.js';
import { RESERVATIONS_EMPTY, CANCEL_COPY, CHANGE_LOCK_COPY, CHANGE_DEADLINE_HOURS } from '../../api/mock.js';
import { useCustomerTabs } from '../useCustomerTabs.js';
import { onPushReceived } from '../../push/pushClient.js';

const REFRESH_MS = 30_000; // 배정·완료 상태를 화면에 머무는 동안 갱신 (외부 검토 3차)
import styles from './Reservations.module.css';

/** 내 예약 화면: 탭(예정/완료/취소)별 예약 카드 목록 + 카드의 변경·취소·영상 보기 + 취소 확인 시트 */
export default function Reservations() {
  const navigate = useNavigate();
  const selectTab = useCustomerTabs(); // 하단 탭바를 눌렀을 때 그 탭 화면으로 보내는 함수
  // 30초마다·배정/완료 푸시가 오면·앱이 다시 보이면 조용히 갱신. 취소 뒤 invalidate 로 늦은 폴링 응답을 버린다 (외부 검토 4차)
  // 푸시 종류는 가리지 않는다 — 앱이 떠 있을 때 온 푸시면 무엇이든 목록을 다시 받는다 (pushClient.js onPushReceived)
  const { data, error, reload, refresh, invalidate } = usePolling(() => getReservations(), [], { interval: REFRESH_MS, subscribe: onPushReceived });
  // 기한이 지난 카드에 "N시간 전까지 할 수 있어요" 만 있어 아직 되는 것처럼 읽혔다 → 안 된다는 것과 연락처 (직접 사용 점검 U2). 번호가 없으면 1:1 문의
  const { data: supportPhone } = useAsync(() => getSupportPhone(), []);
  const [tab, setTab] = useState('upcoming'); // 보고 있는 탭 key: 'upcoming' 예정 | 'done' 완료 | 'cancelled' 취소
  const [cancelId, setCancelId] = useState(null); // 취소 시트를 연 예약 id. null 이면 시트가 닫혀 있다
  const [scope, setScope] = useState('one'); // 정기청소: 'one' 이번 회차만 | 'all' 정기청소 전체
  // 시트의 취소 버튼. 성공하면 시트를 닫고 취소 탭으로 옮겨 알림(토스트)을 띄운다.
  // 실패하면(기한 규칙 등) 시트를 연 채로 cancel.error 문구를 시트 안에 보여 준다
  const cancel = useAction(async () => {
    await cancelReservation(cancelId, { wholeSeries: scope === 'all' });
    invalidate(); // 이 전에 나간 폴링 응답은 버린다
    await refresh().catch(() => {}); // 전체 취소는 여러 카드가 바뀌므로 목록을 다시 받는다. 취소는 됐다 — 목록만 못 받으면 다음 폴링이 메운다 (자체 점검 #48: reload 는 기다려지지 않았다)
    setCancelId(null);
    setTab('cancelled');
    showToast(scope === 'all' ? '정기청소를 취소했어요' : '예약을 취소했어요');
  });
  // 카드의 '예약 취소': 범위를 '이번 회차만' 으로 되돌리고, 이전에 실패한 문구를 지운 뒤 시트를 연다
  const openCancel = (id) => {
    setScope('one');
    cancel.reset();
    setCancelId(id);
  };

  const tabBar = <TabBar active="reservations" onSelect={selectTab} />;
  if (error) return <ScreenError error={error} onRetry={reload} tabBar={tabBar} />;
  if (!data) return <ScreenLoading tabBar={tabBar} />;

  const countOf = (key) => data.reservations.filter((r) => r.tab === key).length; // 그 탭에 속한 예약 수
  // Figma 탭 문구 "예정 2" — 예정 탭에만 건수가 붙는다
  const options = data.tabs.map((t) => ({ key: t.key, label: t.key === 'upcoming' ? `${t.label} ${countOf(t.key)}` : t.label }));
  // 서버 목록은 최신순(내림차순) — 예정 탭만 가까운 방문이 위로 오게 뒤집는다 (검증 검토)
  const list = data.reservations.filter((r) => r.tab === tab);
  // 'YYYY-MM-DD HH:mm' 문자열은 글자 순서가 곧 시간 순서라 문자열 비교로 이른 방문이 앞에 온다. filter 가 만든 새 배열이라 원본 목록은 그대로다
  if (tab === 'upcoming') list.sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  // 취소 시트에 보여 줄 예약 (시트가 닫혀 있으면 null)
  const cancelling = cancelId === null ? null : data.reservations.find((r) => r.id === cancelId);
  // 변경·취소 기한(방문 몇 시간 전까지) — 서버 설정 값(없으면 기본 24). 카드 잠금(changeable)을 정한 것과 같은 값이라
  // 잠긴 카드 안내·정기 회차 취소 시트가 실제 기한을 말한다 (문제 목록 25번)
  const deadlineHours = data.changeDeadlineHours ?? CHANGE_DEADLINE_HOURS;

  return (
    <ScreenLayout noPadding gap="none" tabBar={tabBar}>
      <header className={styles.header}>
        <h1 className={cx('text-heading-m', styles.title)}>{data.header.title}</h1>
        <p className={cx('text-body-s', styles.description)}>{data.header.description}</p>
      </header>
      {options.length > 0 && <UnderlineTabs options={options} value={tab} onChange={setTab} ariaLabel="예약 상태" />}
      <div className={styles.list}>
        {list.length === 0 && <EmptyState icon="calendar" title={RESERVATIONS_EMPTY[tab]} />}
        {/* changeable 이 없으면 바꿀 수 있는 것으로 본다. false(기한 지남)면 카드가 버튼 대신 notChangeableNote 문구를 보여 준다.
            '녹화 영상 보기'(onReplay)는 같은 시청 화면에 &replay=1 을 붙여 녹화본으로 연다 */}
        {list.map((r) => (
          <ReservationCard
            key={r.id}
            title={r.title}
            status={r.status}
            rows={r.rows}
            changeable={r.changeable ?? true}
            notChangeableNote={CHANGE_LOCK_COPY.card(deadlineHours, supportPhone)}
            onChange={() => navigate(`/booking/${r.category}/datetime?edit=${r.id}`, { state: { date: r.date, time: r.time } })} // 현재 일정을 달력에 미리 채운다
            onCancel={() => openCancel(r.id)}
            onWatch={() => navigate(`/service/live?booking=${r.id}`)} // 소개 화면을 거치지 않고 바로 시청
            recordingReady={r.recordingReady}
            onReplay={() => navigate(`/service/live?booking=${r.id}&replay=1`)}
          />
        ))}
      </div>

      {/* 취소 확인 시트. 정기청소 회차(series)면 범위 선택이 보이고, 버튼 문구도 고른 범위를 그대로 말한다 */}
      <Sheet open={cancelId !== null} onClose={() => setCancelId(null)} title={CANCEL_COPY.title}>
        {/* 비슷한 카드가 여러 장(정기 회차)이라 시트만 보고는 어느 방문을 취소하는지 알 수 없었다 (전체 점검 G5) */}
        {cancelling && <p className={cx('text-title-small', styles.description)}>{[cancelling.title, cancelling.rows?.[0]?.value].filter(Boolean).join(' · ')}</p>}
        <p className={cx('text-body-default', styles.description)}>{CANCEL_COPY.description}</p>
        {cancelling?.series && (
          <div role="radiogroup" aria-label="취소 범위" className={styles.scope}>
            {CANCEL_COPY.scopeOptions.map((o) => (
              <RadioOption key={o.key} label={o.label} selected={scope === o.key} onSelect={() => setScope(o.key)} />
            ))}
            {/* 고른 뒤에 나타나면 아래 버튼이 밀려 잘못 눌린다 — 항상 같은 자리에 둔다 (사용성 검토 2026-09-22) */}
            <p className={cx('text-body-s', styles.description)}>{CANCEL_COPY.seriesNote(deadlineHours)}</p>
          </div>
        )}
        <ActionError error={cancel.error} fallback={CANCEL_COPY.failed} />
        <Button variant="destructive" loading={cancel.loading} onClick={cancel.run}>
          {cancelling?.series ? CANCEL_COPY.confirmScope[scope] : CANCEL_COPY.confirm}
        </Button>
        <Button variant="secondary" onClick={() => setCancelId(null)}>
          {CANCEL_COPY.back}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
