// Manager/Profile › 고객센터 · Job/Preparing/Error › 고객센터 문의 — Figma 에 화면 없음 → 1:1 문의 폼 (코드에서 정함). 전화번호는 실서버 설정 후
// 경로 /profile/support. 오는 곳 예: MY 메뉴 '고객센터', 문의 답변 푸시(type inquiry). 뒤로가기는 온 곳으로(navigate(-1)).
// 위에서부터: (전화번호가 설정돼 있으면) 긴급 전화 안내 → 내 문의 목록(MyInquiries) → 문의 입력칸. 보내기는 POST /support/inquiries.
// 안내 문구(copy)는 서버가 아니라 앱의 SUPPORT_COPY 다 (getSupportCopy). 전화번호는 GET /support/contact, 없거나 실패하면 null.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, TextArea, Button, Sheet, ScreenError, ScreenLoading, useAsync, cx, useAction, ActionError } from '@ansimclean/ui';
import { getSupportCopy, getSupportPhone, submitSupportInquiry, newInquiryKey } from '../../api/manager.js';
import MyInquiries from './MyInquiries.jsx';
import { INPUT_LIMITS, URGENT_SUPPORT_COPY } from '../../api/mock.js';
import styles from './profileSub.module.css';

/** 고객센터(1:1 문의) 화면. 접수에 성공하면 완료 시트를 띄우고 '내 문의' 목록을 다시 받게 한다 */
export default function Support() {
  const navigate = useNavigate();
  const { data: copy, error, reload } = useAsync(() => getSupportCopy(), []);
  const { data: supportPhone } = useAsync(() => getSupportPhone(), []); // 번호가 설정돼 있을 때만 전화 버튼 (사용성 검토 2026-09-22)
  const [content, setContent] = useState('');
  const [done, setDone] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0); // 접수 뒤 목록 갱신
  const keyRef = useRef(null); // 같은 내용의 재시도는 같은 키 (두 번 접수되지 않게), 내용을 고치면 새 키
  // 보내기: 실패 문구는 입력칸 아래 ActionError 에 보이고, 입력한 내용은 그대로 남는다
  const send = useAction(async () => {
    // ??= : 값이 비어 있을 때만 새 키를 넣는다 — 실패 뒤 다시 보내면 같은 멱등 키가 가서 서버가 한 번만 접수한다
    keyRef.current ??= newInquiryKey();
    await submitSupportInquiry({ content, submitKey: keyRef.current });
    setDone(true);
    setRefreshKey((k) => k + 1);
  });
  // 입력이 바뀌면 다른 문의이므로 키를 버린다 (다음 보내기에서 새 키)
  const edit = (e) => {
    keyRef.current = null;
    setContent(e.target.value);
  };
  const back = () => navigate(-1); // 뒤로가기·완료 시트 닫기 모두 직전 화면(온 곳)으로
  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="고객센터" onBack={back} />} />;
  if (!copy) return <ScreenLoading navBar={<NavBar title="고객센터" onBack={back} />} />;

  return (
    <ScreenLayout
      navBar={<NavBar title={copy.title} onBack={back} />}
      stickyCta={
        <Button loading={send.loading} disabled={!content.trim()} onClick={send.run}>
          보내기
        </Button>
      }
    >
      {supportPhone && (
        <div className={styles.form}>
          <p className={cx('text-title-card', styles.urgentTitle)}>{URGENT_SUPPORT_COPY.title}</p>
          <p className={cx('text-body-s', styles.secondary)}>{URGENT_SUPPORT_COPY.note}</p>
          {/* tel: 주소를 지금 창에서 열면 기기의 전화 걸기 화면이 뜬다 */}
          <Button variant="secondary" onClick={() => window.open(`tel:${supportPhone}`, '_self')}>
            {URGENT_SUPPORT_COPY.call}
          </Button>
        </div>
      )}
      <MyInquiries refreshKey={refreshKey} />
      <div className={styles.form}>
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
        <TextArea ariaLabel="문의 내용" placeholder={copy.placeholder} value={content} onChange={edit} maxLength={INPUT_LIMITS.inquiry} />
        <ActionError error={send.error} fallback={'전송에 실패했어요. 잠시 후 다시 시도해주세요.'} />
      </div>
      {/* 접수 완료 시트: 닫거나 확인을 누르면 직전 화면으로 돌아간다 */}
      <Sheet open={done} onClose={back} title={copy.done}>
        <p className={cx('text-body-default', styles.secondary)}>답변이 오면 앱 알림으로 알려드리고, 이 화면의 '내 문의' 에서 볼 수 있어요.</p>
        <Button onClick={back}>확인</Button>
      </Sheet>
    </ScreenLayout>
  );
}
