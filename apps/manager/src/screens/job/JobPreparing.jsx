// Figma: Manager/Job/Preparing (2140:2571) + Manager/Job/Preparing/Error (2146:2840) — 한 화면
// 프로토타입: Back → Job/Detail · 카메라 다시 연결 → (장비 상태 다시 조회) · 청소 시작 → Job/InProgress · 고객센터 문의 → Profile/Support (코드에서 정함)
// 두 프레임은 같은 GET /manager/device/status 를 그리므로 하나로 합쳤다(검증 검토): 카메라 행이 issue 면 Error 프레임(배너·시작 비활성), 아니면 Preparing.
// 예전엔 '카메라 다시 연결' 이 아무것도 재시도하지 않고 두 화면을 오갔고, Preparing 은 카메라가 끊겨도 시작을 허용했다.
// (코드에서 정함, B4 §9.2-3) "청소 시작" = startJob(id)(작업 상태) → live.start(id)(라이브 세션) 순서. 서버가 거부하면(당일 아님·다른 작업 진행 중) 그 문구를 보여주고 화면을 옮기지 않는다
// 매니저 앱 /job/:id/preparing 화면. 업무 상세(JobDetail)의 '현장 도착' 에서 온다. 옛 주소 /job/:id/preparing/error 도
// JobPreparingError 가 이리로 보낸다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, CameraStatusItem, AlertIcon, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getEquipmentStatus, getSupportPhone, startJob } from '../../api/manager.js';
import { ACTION_FAILED_COPY, URGENT_SUPPORT_COPY } from '../../api/mock.js';
import { useLiveSession } from '../../live/useLiveSession.js';
import styles from './JobPreparing.module.css';

// 청소 준비 화면. 장비 상태를 받아 카메라 행이 issue 면 연결 실패 모습(시작 비활성, 다시 연결·고객센터 버튼), 아니면 준비 모습을 그린다
export default function JobPreparing() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data: items, error, reload, setData } = useAsync(() => getEquipmentStatus(), []);
  // 현장에서 장비가 죽으면 '영업일 1일 안에 답변' 뿐이라 그날 일이 통째로 날아갔다 → 바로 전화 (사용성 검토 2026-09-22)
  const { data: supportPhone } = useAsync(() => getSupportPhone(), []);
  // "카메라 다시 연결" = 장비 상태만 다시 받는다. reload() 는 data 를 비워 화면 전체가 스켈레톤이 되므로(버튼 스피너가 보일 새가 없다) 제자리에서 받아 덮는다 (검수 4차)
  const recheck = useAction(async () => setData(await getEquipmentStatus()));
  const live = useLiveSession();
  // 청소 시작. ① startJob 은 멱등 키를 붙인다(manager.js). ① 이 실패하면 useAction 이 error 로 받아 문구만 보이고 이 화면에 머문다.
  // ② live.start 는 서버 오류를 던지지 않고 liveClient 상태(error)로 남기므로, ① 이 됐으면 진행 화면으로 간다
  const start = useAction(async () => {
    await startJob(id); // ① 작업 상태 (scheduled → in_progress)
    await live.start(id); // ② 라이브 세션·미리보기 (서버 오류는 던지지 않는다 — 위 설명. 순간 장애면 liveClient 가 10초 뒤 다시 시도)
    navigate('/job/in-progress');
  });

  // 뒤로 가기는 방문 기록과 상관없이 이 작업의 업무 상세로 간다. 오류·로딩 화면도 같은 상단 바를 쓴다
  const navBar = <NavBar title="청소 준비" onBack={() => navigate(`/job/${id}`)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!items) return <ScreenLoading navBar={navBar} />;

  const cameraIssue = items.some((item) => item.label === '카메라' && item.status === 'issue'); // 배터리·네트워크 '확인 필요' 는 시작을 막지 않는다

  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        cameraIssue ? (
          <>
            <ActionError error={recheck.error} fallback={'장비 상태를 다시 받지 못했어요. 다시 시도해주세요.'} />
            <Button loading={recheck.loading} onClick={recheck.run}>
              카메라 다시 연결
            </Button>
            {/* 고객센터 번호가 설정돼 있을 때만 전화 버튼 (getSupportPhone 은 번호가 없거나 조회가 실패하면 null) */}
            {supportPhone && (
              <Button variant="secondary" onClick={() => window.open(`tel:${supportPhone}`, '_self')}>
                {URGENT_SUPPORT_COPY.call}
              </Button>
            )}
            <Button variant="secondary" onClick={() => navigate('/profile/support')}>
              고객센터 문의
            </Button>
            <Button variant="secondary" disabled>
              청소 시작 (카메라 연결 후 가능)
            </Button>
          </>
        ) : (
          <>
            <ActionError error={recheck.error} fallback={'장비 상태를 다시 받지 못했어요. 다시 시도해주세요.'} />
            <Button variant="secondary" loading={recheck.loading} onClick={recheck.run}>
              카메라 다시 연결
            </Button>
            <ActionError error={start.error} fallback={ACTION_FAILED_COPY.start} />
            {/* 시작 요청 중이거나, 이 작업의 라이브 세션이 아직 연결 중이면 스피너 */}
            <Button loading={start.loading || (live.jobId === id && live.status === 'connecting')} onClick={start.run}>
              청소 시작
            </Button>
          </>
        )
      }
    >
      {/* 위쪽: 카메라 연결 실패면 경고 배너(Error 프레임), 아니면 제목·안내(Preparing 프레임) */}
      {cameraIssue ? (
        <Card tone="danger" padding="alert" gap="smd" className={styles.banner}>
          <div className={styles.bannerTop}>
            <AlertIcon />
            <h1 className="text-heading-s">카메라 연결 실패</h1>
          </div>
          <p className={cx('text-body-default', styles.primary)}>
            안심캠이 연결되지 않아 청소를 시작할 수 없습니다. 장비 전원과 블루투스를 확인해주세요.
          </p>
        </Card>
      ) : (
        <header className={styles.head}>
          <h1 className="text-heading-m">청소 준비</h1>
          <p className={cx('text-body-s', styles.secondary)}>촬영 장비 상태를 확인한 뒤 청소를 시작해주세요.</p>
        </header>
      )}

      {/* 촬영 장비 행 (서버 값: 카메라·배터리·네트워크·실시간 전송) */}
      <section className={styles.block}>
        <h2 className="text-title-card">촬영 장비</h2>
        <Card padding="list" gap="none" divided>
          {items.map((item) => (
            <CameraStatusItem key={item.label} status={item.status} label={item.label} value={item.value} />
          ))}
        </Card>
      </section>
    </ScreenLayout>
  );
}
