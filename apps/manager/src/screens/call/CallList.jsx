// Figma: Manager/Call/List (2122:2264) · Manager/Call/Empty (2146:2896) — calls 가 비면 Empty 상태
// 프로토타입: CallCard → Call/Detail, 수락 → Job/Detail, 거절 → Call/RejectSheet(M2), 탭 → 각 탭 화면
// (코드에서 정함, B4 §9.2-2) 수락 = acceptCall(id) → { jobId } → /job/{jobId}. 이미 마감(OFFER_TAKEN·OFFER_EXPIRED)이면 시트 "이미 마감된 요청이에요" → 확인하면 목록 새로고침
// 매니저 앱 콜 탭 화면 (라우트 /call). 대기 중인 콜(매니저에게 온 작업 제안)을 getCalls 로 받아 카드로 나열한다.
// 목록은 usePolling 으로 조용히 다시 받고(아래 설명), 카드마다 만료까지 남은 시간을 1초마다 다시 그린다.
// 카드에서 바로 수락(→ 업무 상세)·거절(→ 사유 시트)하고, 카드 자체를 탭하면 콜 상세(/call/:id)로 간다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, ManagerTabBar, CallCard, Card, Dot, EmptyState, Sheet, Button, cx, usePolling, useAction, useCountdown, secondsUntil, mmss, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getCalls, acceptCall, rejectCall, getRejectReasons, isCallClosed } from '../../api/manager.js';
import ReasonList from './ReasonList.jsx';
import { onPushReceived } from '../../push/pushClient.js';
import { CALL_CLOSED_COPY, ACTION_FAILED_COPY } from '../../api/mock.js';
import { useManagerTabs } from '../useManagerTabs.js';
import styles from './CallList.module.css';

/**
 * 콜 목록 자동 갱신 간격 (코드에서 정함).
 * 푸시 구독(onPushReceived)과 같이 돈다 — 푸시를 놓쳐도 새 콜이 30초 안에 보이게
 */
const CALL_REFRESH_MS = 30000;

// 콜 목록 화면 컴포넌트. 콜이 없으면 빈 상태(Figma Call/Empty)와 근무 상태 카드를 그린다.
export default function CallList() {
  const navigate = useNavigate();
  const goTab = useManagerTabs(); // 하단 탭을 누르면 그 탭 경로로 이동하는 함수
  // 30초마다·새 콜 푸시가 오면·앱이 다시 보이면 조용히 갱신.
  // 거절 성공 뒤·마감 시트를 닫을 때 invalidate 로 늦은 폴링 응답이 닫힌 콜을 되살리지 않게
  const { data, error, reload, refresh, invalidate } = usePolling(() => getCalls(), [], { interval: CALL_REFRESH_MS, subscribe: onPushReceived });
  const [acceptingId, setAcceptingId] = useState(null); // 수락 중인 카드 id — 그 카드만 스피너, 나머지 카드는 버튼을 막는다
  // 수락: 성공하면 서버가 준 jobId 의 업무 상세로. 실패는 accept.error 에 남아 위쪽 ActionError·마감 시트가 본다.
  // finally: 성공·실패 어느 쪽이든 카드 스피너(acceptingId)를 푼다
  const accept = useAction(async (id) => {
    setAcceptingId(id);
    try {
      const { jobId } = await acceptCall(id);
      navigate(`/job/${jobId}`);
    } finally {
      setAcceptingId(null);
    }
  });
  // 거절은 목록에서 바로 (상세를 거치면 조회 두 번 + 히스토리에 ?reject=1 이 남아 뒤로가기가 마감 오류를 열었다, 검증 검토)
  const [rejectId, setRejectId] = useState(null); // 거절 시트를 연 콜 id. null 이면 시트가 닫혀 있다
  const [reason, setReason] = useState(null); // 시트에서 고른 사유
  // 거절: 사유를 보낸다. 실패는 다시 던져 reject.error 에 남긴다. 마감 오류면 그 전에 거절 시트를 닫는다(→ 마감 시트).
  // 서버는 시트를 연 사이 닫힌 콜(다른 매니저 수락·고객 취소·만료)을 OFFER_TAKEN·OFFER_EXPIRED 로, 내가 이미 수락한 콜을 INVALID_STATE 로 답한다
  // (api.md rejectCall, 문제 목록 10번). INVALID_STATE 는 거절 시트 안에 서버 문구로 보인다.
  // 성공하면 시트를 닫고, 나가 있는 폴링 응답을 버린 뒤(invalidate) 목록을 다시 받는다 — 옛 응답이 거절한 콜을 되살리지 않게
  const reject = useAction(async () => {
    try {
      await rejectCall(rejectId, reason);
    } catch (e) {
      if (isCallClosed(e)) setRejectId(null); // 마감 시트가 위에 겹치지 않게
      throw e;
    }
    setRejectId(null);
    invalidate();
    refresh().catch(() => {}); // 목록을 비우지 않고 조용히 (자체 점검 #48)
  });
  // 거절 시트 열기: 이전 실패 문구와 고른 사유를 지우고 연다. 기본 사유는 목록이 오면 ReasonList 의 onLoaded 가 첫 사유로 채운다
  const openReject = (id) => {
    reject.reset();
    setReason(null);
    setRejectId(id);
  };
  // 마감 시트 닫기: 수락·거절의 마감 오류를 지워 시트를 닫고, 옛 폴링 응답을 버린 뒤 마감된 콜이 빠진 목록을 다시 받는다
  const closeClosedSheet = () => {
    accept.reset();
    reject.reset();
    invalidate();
    refresh().catch(() => {}); // 실패해도 받아 둔 목록은 그대로 둔다 (다음 폴링이 다시 받는다)
  };


  // 가장 늦은 만료 시각. ISO 문자열을 글자로 비교한다(같은 형식이면 시각 순서와 같다).
  // 이 시각 하나만 구독해도 그때까지 1초마다 화면 전체가 다시 그려져, 카드마다 계산한 남은 시간이 함께 줄어든다
  const lastExpiry = (data?.calls ?? []).reduce((max, c) => (c.expiresAt && (!max || c.expiresAt > max) ? c.expiresAt : max), null);
  useCountdown(lastExpiry); // 1초마다 다시 그린다 (훅은 early return 위에)
  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<ManagerTabBar active="call" onSelect={goTab} />} />;
  if (!data) return <ScreenLoading tabBar={<ManagerTabBar active="call" onSelect={goTab} />} />;
  // approved = 지금 콜을 받을 수 있나(미승인·정지면 false). 홈(getHome)의 manager.approved 는 승인만 봐서 뜻이 다르다
  const { availability, tabBadge, approved = true } = data; // approved 가 오지 않으면 승인된 것으로 보고 근무 상태 점을 success 색으로
  // 만료 카운트다운: 가장 늦은 만료까지 1초마다 다시 그리고, 지난 카드는 목록에서 뺀다 (검증 검토 — 문자열 '4:32 후 만료' 가 30초 폴링까지 멈춰 있었다)
  const calls = data.calls.filter((c) => !c.expiresAt || secondsUntil(c.expiresAt) > 0);
  const expiryOf = (c) => (c.expiresAt ? `${mmss(secondsUntil(c.expiresAt))} 후 만료` : c.expiry); // expiresAt 이 없으면 서버 문구 그대로
  const isEmpty = calls.length === 0; // 만료된 카드를 뺀 뒤 기준이라, 마지막 콜이 만료되면 곧바로 빈 상태가 된다

  // 탭바의 콜 배지 숫자 tabBadge 는 getCalls 가 받은 콜 건수로 채운다 (만료 카드를 빼기 전 건수)
  return (
    <ScreenLayout gap="md" tabBar={<ManagerTabBar active="call" onSelect={goTab} showCallBadge={tabBadge > 0} callBadgeCount={tabBadge} />}>
      {isEmpty ? (
        <>
          {/* 빈 상태: 안내 + 근무 상태 카드 (상태·설명 문구는 서버 availability 그대로) */}
          <h1 className="text-heading-s">콜</h1>
          <EmptyState icon="call" title="대기 중인 청소 요청이 없어요" description="새로운 요청이 들어오면 알림으로 알려드릴게요." />
          <Card tone="subtle" padding="sm" gap="sm">
            <div className={styles.rowStart}>
              <Dot tone={approved ? 'success' : 'live'} />
              <span className="text-title-small">{availability.status}</span>
            </div>
            <p className={cx('text-body-s', styles.secondary)}>{availability.detail}</p>
          </Card>
        </>
      ) : (
        <>
          <header className={styles.header}>
            <h1 className="text-heading-s">새로운 청소 요청</h1>
            <p className={cx('text-body-s', styles.secondary)}>수락 또는 거절을 선택해주세요</p>
            <ActionError error={accept.error && !isCallClosed(accept.error) ? accept.error : null} fallback={ACTION_FAILED_COPY.accept} />
          </header>
          {/* 카드: 수락 중인 카드만 스피너(loading), 그동안 다른 카드는 disabled. 카드 자체를 탭하면 콜 상세 */}
          {calls.map((call) => (
            <CallCard
              key={call.id}
              expiry={expiryOf(call)}
              title={call.title}
              location={call.location}
              pay={call.pay}
              eta={call.eta}
              details={call.details}
              note={call.note}
              loading={acceptingId === call.id}
              disabled={accept.loading && acceptingId !== call.id}
              onAccept={() => accept.run(call.id)}
              onReject={() => openReject(call.id)}
              onOpen={() => navigate(`/call/${call.id}`)}
            />
          ))}
        </>
      )}

      {/* 거절 시트: 열릴 때 사유를 받고, 목록이 오면 고른 사유가 없을 때 첫 사유를 기본 선택한다. 사유가 없으면 거절하기를 막는다 */}
      <Sheet open={rejectId !== null} onClose={() => setRejectId(null)} title="이 청소 요청을 거절하시겠어요?">
        <ReasonList open={rejectId !== null} load={getRejectReasons} ariaLabel="거절 사유" value={reason} onChange={setReason} onLoaded={(list) => setReason((r) => r ?? list[0])} />
        {/* 실패 문구: 서버 문구(예: '이미 수락한 콜이에요 …')를 보이고, 없을 때만 ACTION_FAILED_COPY.reject. 마감 오류는 아래 마감 시트가 따로 */}
        <ActionError error={reject.error && !isCallClosed(reject.error) ? reject.error : null} fallback={ACTION_FAILED_COPY.reject} />
        <Button loading={reject.loading} disabled={!reason} onClick={reject.run}>
          거절하기
        </Button>
      </Sheet>

      {/* 마감 시트: 수락·거절이 마감 오류(OFFER_TAKEN·OFFER_EXPIRED)로 거부됐을 때. 닫거나 확인하면 목록을 새로 받는다 */}
      <Sheet open={isCallClosed(accept.error) || isCallClosed(reject.error)} onClose={closeClosedSheet} title={CALL_CLOSED_COPY.title}>
        <Button onClick={closeClosedSheet}>{CALL_CLOSED_COPY.confirm}</Button>
      </Sheet>
    </ScreenLayout>
  );
}
