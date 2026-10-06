// Figma: Manager/Call/Detail (2122:2351) + Manager/Call/RejectSheet (2122:2395, 바텀시트)
// 프로토타입: Back → Call/List · 거절 → RejectSheet · 수락 → Job/Detail · RejectSheet 거절하기 → Call/List
// (코드에서 정함, B4) 수락·거절은 서버 호출(useAction). 이미 마감된 콜이면 시트 → 확인 시 목록으로
// 매니저 앱 콜 상세 화면 (라우트 /call/:id, 콜 목록에서 카드를 탭하면 온다).
// 흐름: getCall(id) 로 콜 하나를 받고 → 만료까지 1초마다 남은 시간을 다시 그린다 →
// 수락하면 서버가 준 jobId 의 업무 상세로, 거절하면 사유 시트에서 골라 보낸 뒤 콜 목록으로 간다.
// 만료가 지났거나 다른 매니저가 먼저 수락한 콜은 마감 안내(CALL_CLOSED_COPY)를 보인다.
import { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, CallCard, Card, Sheet, Button, cx, useAsync, useAction, useCountdown, secondsUntil, mmss, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getCall, getRejectReasons, rejectCall, acceptCall, isCallClosed } from '../../api/manager.js';
import { CALL_CLOSED_COPY, ACTION_FAILED_COPY } from '../../api/mock.js';
import ReasonList from './ReasonList.jsx';
import styles from './CallDetail.module.css';

// 콜 상세 화면 컴포넌트. 주소의 :id 로 콜을 조회하고, 수락·거절 버튼과 거절 시트·마감 시트를 그린다.
export default function CallDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { data: call, error, reload } = useAsync(() => getCall(id), [id]);
  const [sheetOpen, setSheetOpen] = useState(searchParams.get('reject') === '1'); // 주소에 ?reject=1 이 붙어 오면 거절 시트를 연 채로 시작한다
  const [reason, setReason] = useState(null); // 사유 목록은 시트 안 ReasonList 가 열릴 때 받는다 (검증 검토)
  // 수락: 서버가 콜을 작업으로 만들고 준 jobId 의 업무 상세로 간다. 멱등 키는 acceptCall 안에서 붙는다.
  // 실패는 accept.error 로 남고, 아래 ActionError·마감 시트(closed)가 그것을 본다
  const accept = useAction(async () => {
    const { jobId } = await acceptCall(id);
    navigate(`/job/${jobId}`);
  });
  // 거절: 시트에서 고른 사유를 보낸다. 실패는 다시 던져 reject.error 에 남긴다 — 마감 오류(OFFER_TAKEN·OFFER_EXPIRED)면 아래 closed 가 마감 시트를 열고,
  // 내가 이미 수락한 콜(INVALID_STATE)이면 거절 시트 안에 서버 문구가 보인다 (api.md rejectCall, 문제 목록 10번)
  const reject = useAction(async () => {
    try {
      await rejectCall(id, reason);
    } catch (e) {
      if (isCallClosed(e)) setSheetOpen(false); // 마감 시트가 위에 겹치지 않게 거절 시트는 닫는다
      throw e;
    }
    setSheetOpen(false);
    navigate('/call', { replace: true }); // 뒤로가기로 방금 거절한 콜(마감 오류)이 다시 열리지 않게
  });
  useCountdown(call?.expiresAt ?? null); // 1초마다 다시 그린다 — 서버 문자열 그대로면 5분을 읽어도 '4:32 후 만료' 였다 (자체 점검 #46, 목록과 같다)
  const closed = isCallClosed(accept.error) || isCallClosed(reject.error); // 수락·거절이 늦어 서버가 거부했다(OFFER_TAKEN·OFFER_EXPIRED) → 마감 시트
  const navBar = <NavBar title="청소 요청 상세" onBack={() => navigate('/call')} />; // 오류·로딩 화면에도 같은 상단 바 (뒤로가기 → 콜 목록)

  // 이미 마감된 콜(만료·다른 매니저 수락)을 열면 '네트워크 확인' 이 아니라 마감 안내 (코드에서 정함)
  if (isCallClosed(error)) return <ScreenError navBar={navBar} title={CALL_CLOSED_COPY.title} description={CALL_CLOSED_COPY.description} retryLabel={CALL_CLOSED_COPY.confirm} onRetry={() => navigate('/call')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!call) return <ScreenLoading navBar={navBar} />;
  // 보는 중에 만료 시각이 지나면(useCountdown 이 1초마다 다시 그린다) 카드 대신 마감 안내로 바꾼다. expiresAt 이 없으면 만료 판정을 하지 않는다
  const expired = Boolean(call.expiresAt) && secondsUntil(call.expiresAt) <= 0;
  if (expired) return <ScreenError navBar={navBar} title={CALL_CLOSED_COPY.title} description={CALL_CLOSED_COPY.description} retryLabel={CALL_CLOSED_COPY.confirm} onRetry={() => navigate('/call')} />;

  return (
    <ScreenLayout gap="md" navBar={<NavBar title="청소 요청 상세" onBack={() => navigate('/call')} />}>
      {/* 만료 문구: expiresAt 이 있으면 남은 시간을 m:ss 로 계산하고, 없으면 서버 문구(expiry) 그대로 */}
      <CallCard
        expiry={call.expiresAt ? `${mmss(secondsUntil(call.expiresAt))} 후 만료` : call.expiry}
        title={call.title}
        location={call.location}
        pay={call.pay}
        eta={call.eta}
        details={call.details}
        note={call.note}
        loading={accept.loading}
        onAccept={accept.run}
        onReject={() => setSheetOpen(true)}
      />
      {/* 수락 실패 문구. 마감 오류는 마감 시트가 따로 보여 주므로 뺀다. 서버 문구가 없을 때만 fallback */}
      <ActionError error={accept.error && !closed ? accept.error : null} fallback={ACTION_FAILED_COPY.accept} />
      <Card tone="muted" padding="xs" gap="xs">
        <h2 className="text-title-card">고객 요청사항</h2>
        <p className={cx('text-body-s', styles.secondary)}>{call.customerRequest}</p>
      </Card>

      {/* 거절 시트: 열릴 때 사유 목록을 받고(ReasonList), 받으면 아직 고른 사유가 없을 때 첫 사유를 기본 선택한다.
          실패 문구는 서버 문구(예: '이미 수락한 콜이에요 …'), 없을 때만 ACTION_FAILED_COPY.reject (fallback) */}
      <Sheet open={sheetOpen} onClose={() => setSheetOpen(false)} title="이 청소 요청을 거절하시겠어요?">
        <ReasonList open={sheetOpen} load={getRejectReasons} ariaLabel="거절 사유" value={reason} onChange={setReason} onLoaded={(list) => setReason((r) => r ?? list[0])} />
        <ActionError error={reject.error && !closed ? reject.error : null} fallback={ACTION_FAILED_COPY.reject} />
        <Button loading={reject.loading} disabled={!reason} onClick={reject.run}>
          거절하기
        </Button>
      </Sheet>

      {/* 마감 시트: 수락·거절이 마감 오류로 거부됐을 때. 닫거나 확인하면 콜 목록으로 */}
      <Sheet open={closed} onClose={() => navigate('/call')} title={CALL_CLOSED_COPY.title}>
        <Button onClick={() => navigate('/call')}>{CALL_CLOSED_COPY.confirm}</Button>
      </Sheet>
    </ScreenLayout>
  );
}
