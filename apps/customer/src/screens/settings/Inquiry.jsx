// Settings › 1:1 문의하기 (Figma 에 화면 없음 — 예약 요청사항 화면의 TextArea 재사용, 접수되면 시트로 알림)
// 들어오는 길: 설정 화면의 '1:1 문의하기' 메뉴(/settings/inquiry). 위쪽에 내 문의 목록(MyInquiries), 아래에 입력칸.
// 흐름: 보내기 → POST /support/inquiries (멱등 키 포함) → 완료 시트 + 목록 다시 받기 → 확인·닫기 시 설정 화면으로.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, TextArea, Button, Sheet, cx, useAction, ActionError } from '@ansimclean/ui';
import { submitInquiry, newInquiryKey } from '../../api/customer.js';
import { INQUIRY_COPY, INPUT_LIMITS } from '../../api/mock.js';
import MyInquiries from './MyInquiries.jsx';
import styles from './settingsSub.module.css';

/** 1:1 문의 화면: 내 문의 목록 + 문의 입력칸 + 접수 완료 시트 */
export default function Inquiry() {
  const navigate = useNavigate();
  const [content, setContent] = useState('');
  const [done, setDone] = useState(false); // 접수에 성공했는가 — true 면 완료 시트를 연다
  const [refreshKey, setRefreshKey] = useState(0); // 접수 뒤 목록 갱신
  const keyRef = useRef(null); // 같은 내용의 재시도는 같은 키 (두 번 접수되지 않게), 내용을 고치면 새 키

  // 보내기: 키가 없을 때만 새로 만든다(??=). 실패 뒤 내용을 안 고치고 다시 누르면 같은 키로 보내 서버가 한 번만 접수한다
  const send = useAction(async () => {
    keyRef.current ??= newInquiryKey();
    await submitInquiry({ content, submitKey: keyRef.current });
    setDone(true);
    setRefreshKey((k) => k + 1);
  });
  // 입력: 내용이 바뀌면 다른 문의이므로 키를 버린다 (다음 보내기에서 새 키)
  const edit = (e) => {
    keyRef.current = null;
    setContent(e.target.value);
  };

  return (
    <ScreenLayout
      navBar={<NavBar title={INQUIRY_COPY.title} onBack={() => navigate('/settings')} />}
      stickyCta={
        <Button loading={send.loading} disabled={!content.trim()} onClick={send.run}>
          보내기
        </Button>
      }
    >
      <MyInquiries refreshKey={refreshKey} />
      <div className={styles.form}>
        <p className={cx('text-body-s', styles.secondary)}>{INQUIRY_COPY.description}</p>
        <TextArea ariaLabel="문의 내용" placeholder={INQUIRY_COPY.placeholder} value={content} onChange={edit} maxLength={INPUT_LIMITS.inquiry} />
        <ActionError error={send.error} fallback={'전송에 실패했어요. 잠시 후 다시 시도해주세요.'} />
      </div>
      <Sheet open={done} onClose={() => navigate('/settings')} title={INQUIRY_COPY.done}>
        <p className={cx('text-body-default', styles.secondary)}>답변이 오면 앱 알림으로 알려드리고, 이 화면의 '내 문의' 에서 볼 수 있어요.</p>
        <Button onClick={() => navigate('/settings')}>확인</Button>
      </Sheet>
    </ScreenLayout>
  );
}
