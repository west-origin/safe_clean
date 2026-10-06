// Figma: Manager/Job/Detail (2140:2502)
// 프로토타입: Back → Home · 현장 도착 → Job/Preparing. 지도 보기·고객 연락은 연결 없음 → 지도 앱·전화 (코드에서 정함)
// "방문 취소" 는 Figma 에 없다 — 매니저 정책(docs/manager-policy.md, 사용자 승인 2026-09-22)으로 추가.
//   서버가 구간(24시간 전/이내)을 판단해 패널티를 매기고, 예약은 취소되지 않고 다시 매칭된다. 4시간 안에는 서버가 막고 그 문구를 보여 준다.
// 매니저 앱 /job/:id 화면. 들어오는 곳 예: 홈·일정 탭의 일정 카드, 콜 수락 직후(CallList).
// 작업 상세(getJob)와 고객 연락처(getJobContact)를 따로 받는다. 연락처는 서버가 방문일 ±1일에만 주고,
// 그 밖에는 null 이라 '지도 보기'·'고객 연락' 버튼이 비활성이다.
import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, StatusBadge, Card, Button, ChecklistItem, Sheet, RadioOption, TextArea, ActionError, ErrorState, InfoNote, cx, useAsync, usePolling, useAction, ScreenError, ScreenLoading, showToast } from '@ansimclean/ui';
import { getJob, getJobContact, getCancelReasons, cancelJob } from '../../api/manager.js';
import { JOB_CANCEL_COPY, INPUT_LIMITS } from '../../api/mock.js';
import { onPushReceived } from '../../push/pushClient.js';
import styles from './JobDetail.module.css';

// 보고 있는 동안 고객의 취소·일정 변경이 보이게 다시 받는 간격 (푸시가 오면 바로). 실패하면 화면은 그대로 둔다(usePolling)
const JOB_REFRESH_MS = 30_000;
const PHOTO_REUSE_MS = 60 * 60_000; // 사진 서명 URL(24시간)을 다시 쓰는 한도 — 폴링마다 새 URL 로 사진을 다시 내려받지 않게

// 업무 상세. 하단 버튼은 작업 상태로 고른다: scheduled 면 '현장 도착'(당일만)·'방문 취소', inProgress 면 진행 화면으로,
// paused 면 일시정지 화면(재개)으로, 그 밖(complete)은 버튼이 없다.
// 방문 취소 시트는 사유(서버 목록, 필수) + 상세(선택)를 cancelJob 으로 보내고, 성공하면 토스트를 띄우고 홈으로 간다.
export default function JobDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  // 폴링마다 서버가 사진 서명 URL 을 새로 만든다 — 같은 작업·같은 장수면 한 시간 동안은 처음 받은 URL 을 그대로 써서
  // 사진이 다시 그려지거나 다시 내려받아지지 않게 한다
  const lastJob = useRef(null);
  const loadJob = async () => {
    const next = await getJob(id);
    const prev = lastJob.current;
    const reuse = prev && prev.job.id === next.id && prev.job.photos?.length === next.photos?.length && Date.now() - prev.at < PHOTO_REUSE_MS;
    const job = reuse ? { ...next, photos: prev.job.photos } : next;
    lastJob.current = { job, at: reuse ? prev.at : Date.now() };
    return job;
  };
  // 고객이 취소하면 서버는 409('취소된 예약이에요'), 일정을 바꾸면(매니저 칸이 비어) 404 를 준다. 조용한 갱신은 실패를 버리므로,
  // 이 두 경우만 처음부터 다시 받아 오류 화면(서버 문구)을 보인다 — 옛 작업의 '현장 도착'·'방문 취소' 가 남지 않게. 내가 취소하는 중이면 그대로 둔다
  const { data: job, error, reload } = usePolling(loadJob, [id], {
    interval: JOB_REFRESH_MS,
    subscribe: onPushReceived,
    onError: (e) => (e?.status === 404 || e?.status === 409) && !cancel.loading && reload(),
  });
  const contactQ = useAsync(() => getJobContact(id), [id]); // 404(방문일 ±1일 밖·취소된 작업 등) 는 null, 그 밖의 실패는 error
  const contact = contactQ.data;
  // 방문 취소 시트: 열림 여부, 고른 사유 키, 상세 입력
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState(null);
  const [detail, setDetail] = useState('');
  // 취소 사유 목록은 화면을 열 때 같이 받는다. 실패는 화면 전체가 아니라 시트 안(ErrorState)에서만 보여 준다
  const reasonsQ = useAsync(() => getCancelReasons(), []);
  // 취소 요청. cancelJob 이 멱등 키를 붙여(manager.js) 다시 눌러도 한 번만 처리된다. 성공한 뒤에만 시트를 닫고 홈으로
  const cancel = useAction(async () => {
    await cancelJob(id, { reason, detail });
    setCancelOpen(false);
    showToast(JOB_CANCEL_COPY.done);
    navigate('/');
  });
  // 오류 화면의 '다시 시도': 상세와 연락처를 둘 다 처음부터 다시 받는다
  const reloadAll = () => {
    reload();
    contactQ.reload();
  };
  // 일정 탭(달력)·홈·콜 수락 세 곳에서 들어온다 — 늘 홈으로 떨어지지 않게 온 곳으로. 히스토리가 없으면(푸시 딥링크) 홈 (검증 검토)
  const back = () => (window.history.length > 1 ? navigate(-1) : navigate('/'));

  if (error || contactQ.error) return <ScreenError error={error ?? contactQ.error} onRetry={reloadAll} navBar={<NavBar title="업무 상세" onBack={back} />} />;
  if (!job) return <ScreenLoading navBar={<NavBar title="업무 상세" onBack={back} />} />;

  return (
    <ScreenLayout
      navBar={<NavBar title="업무 상세" onBack={back} />}
      stickyCta={
        job.status === 'scheduled' ? (
          <>
            {/* 시작은 작업 당일에만 된다(서버) — 전날에 누르면 준비 화면까지 갔다가 막혔다 (직접 사용 점검 U11) */}
            {job.today ? <Button onClick={() => navigate(`/job/${id}/preparing`)}>현장 도착</Button> : <InfoNote glyph="ⓘ">{JOB_CANCEL_COPY.notToday}</InfoNote>}
            <Button variant="secondary" chevron={false} onClick={() => setCancelOpen(true)}>
              {JOB_CANCEL_COPY.button}
            </Button>
          </>
        ) : job.status === 'inProgress' || job.status === 'paused' ? (
          // 진행 중이면 진행 화면으로, 일시정지(서버 JobService.badge 가 'paused' 로 따로 준다)면 재개할 수 있는 일시정지 화면으로 —
          // 홈의 진행 중 카드와 같은 갈림이고 문구도 같다. '바디캠 연결이 끊겼어요' 푸시가 여기로 와 막다른 곳이었고 (자체 점검 #32),
          // 일정 탭에서 일시정지된 작업을 열면 버튼이 없었다 (문제 목록 11번)
          <Button onClick={() => navigate(job.status === 'paused' ? '/job/paused' : '/job/in-progress')}>업무 화면으로 돌아가기</Button>
        ) : undefined /* 완료된 작업은 시작할 수 없다(서버 INVALID_STATE) — 버튼을 두지 않는다 (코드에서 정함) */
      }
    >
      <header className={styles.head}>
        <div>
          <StatusBadge status={job.status} />
        </div>
        <h1 className="text-heading-m">{job.startLabel}</h1>
      </header>

      {/* 주소 카드: 서버 address 배열(도로명, 상세 주소)을 줄바꿈으로 잇고, 아래에 지도·전화 버튼 */}
      <Card tone="muted" padding="sm" gap="md" className={styles.address}>
        <p className="text-body-m">
          {job.address.map((line, i) => (
            <span key={line}>
              {i > 0 && <br />}
              {line}
            </span>
          ))}
        </p>
        <div className={styles.contactRow}>
          {/* Figma 프로토타입 연결 없음 → 지도 앱(카카오맵 검색)·전화 걸기 (코드에서 정함) */}
          <Button variant="outlineGray" disabled={!contact} onClick={() => window.open(`https://map.kakao.com/link/search/${encodeURIComponent(contact.mapQuery)}`, '_blank', 'noopener')}>
            지도 보기
          </Button>
          <Button variant="outlineGray" disabled={!contact} onClick={() => window.open(`tel:${contact.phone}`, '_self')}>
            고객 연락
          </Button>
        </div>
      </Card>

      {/* 정보 블록: 서버 info 행(고객·청소 유형·예상 소요, 있으면 주거 정보)을 라벨/값으로, 마지막 줄은 예상 정산 금액 */}
      <dl className={styles.info}>
        {job.info.map((row) => (
          <div key={row.label} className={styles.infoRow}>
            <dt className={cx('text-body-s', styles.infoLabel)}>{row.label}</dt>
            <dd className="text-body-default">{row.value}</dd>
          </div>
        ))}
        <div className={styles.infoRow}>
          <dt className={cx('text-body-s', styles.infoLabel)}>예상 정산</dt>
          <dd className={cx('text-title-card', styles.pay)}>{job.pay}</dd>
        </div>
      </dl>

      {job.scope.length > 0 && ( // 옵션이 없는 예약(수리 등)에서 제목만 덩그러니 남았다 (직접 사용 점검 U10)
        <section className={styles.scope}>
          <h2 className="text-title-card">청소 범위</h2>
          <div role="list">
            {job.scope.map((item) => (
              <ChecklistItem key={item} label={item} checked readOnly />
            ))}
          </div>
        </section>
      )}

      {/* 고객 요청사항(서버 requests 배열 그대로) + 수리 예약이면 첨부 사진 */}
      <section className={styles.requests}>
        <h2 className="text-title-card">고객 요청사항</h2>
        <Card tone="muted" padding="xs" gap="none">
          <ul className={cx('text-body-default', styles.bullets)}>
            {job.requests.map((r) => (
              <li key={r}>• {r}</li>
            ))}
          </ul>
        </Card>
        {job.photos?.length > 0 && (
          /* 수리 예약의 첨부 사진 (Figma 매니저 화면에 없음 — 코드에서 정함). 탭하면 원본을 새 창으로 */
          <ul className={styles.photos} aria-label="고객이 첨부한 사진">
            {job.photos.map((url, i) => (
              <li key={url}>
                <a href={url} target="_blank" rel="noreferrer" className={styles.photoLink} aria-label={`첨부 사진 ${i + 1} 크게 보기`}>
                  <img src={url} alt="" className={styles.photo} />
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* 방문 취소 시트 (문구는 mock.js JOB_CANCEL_COPY). 사유를 고르기 전에는 확인 버튼이 비활성이다 */}
      <Sheet open={cancelOpen} onClose={() => setCancelOpen(false)} title={JOB_CANCEL_COPY.title}>
        <p className={cx('text-body-default', styles.sheetText)}>{JOB_CANCEL_COPY.description}</p>
        {reasonsQ.error ? (
          <ErrorState error={reasonsQ.error} onRetry={reasonsQ.reload} /> /* 사유를 못 받으면 빈 목록 + 영영 비활성 버튼이었다 (자체 점검 #45) */
        ) : (
          <div role="radiogroup" aria-label={JOB_CANCEL_COPY.reasonLabel} className={styles.reasons}>
            {(reasonsQ.data ?? []).map((r) => (
              <RadioOption key={r.key} label={r.label} selected={reason === r.key} onSelect={() => setReason(r.key)} />
            ))}
          </div>
        )}
        {/* 상세 내용(선택). 길이 상한은 서버 ManagerCancelRequest 와 같은 INPUT_LIMITS.jobCancelDetail(500자) — 더 받으면 확인 때 서버가 400 으로 거절한다.
            비어 있으면 cancelJob 이 null 로 보낸다 */}
        <TextArea ariaLabel={JOB_CANCEL_COPY.detailLabel} placeholder={JOB_CANCEL_COPY.detailPlaceholder} value={detail} onChange={(e) => setDetail(e.target.value)} maxLength={INPUT_LIMITS.jobCancelDetail} />
        <ActionError error={cancel.error} fallback={JOB_CANCEL_COPY.failed} />
        <Button variant="destructive" disabled={!reason} loading={cancel.loading} onClick={cancel.run}>
          {JOB_CANCEL_COPY.confirm}
        </Button>
        <Button variant="secondary" onClick={() => setCancelOpen(false)}>
          {JOB_CANCEL_COPY.back}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
