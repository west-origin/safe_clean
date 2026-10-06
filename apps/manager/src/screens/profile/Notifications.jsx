// Manager/Profile › 알림 설정 — Figma 에 화면 없음 → 고객용 설정과 같은 토글 목록 (코드에서 정함)
// 경로 /profile/notifications — MY 메뉴 '알림 설정' 에서 온다.
// 서버: 목록 { rows: [{ key, label }], values: { key: 켜짐 여부 } } 는 GET /manager/notifications, 토글 하나는 PUT /manager/notifications/{key}.
// '푸시 알림'(key push)이 켜져 있어도 기기(OS) 알림 권한이 거부됐거나 기기 토큰 발급에 실패했으면(pushClient 상태 denied·error)
// 알림이 오지 않는다 — 그때만 안내 문구를 띄운다.
import { useRef, useState, useSyncExternalStore } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, MenuGroup, MenuRow, ScreenError, ScreenLoading, useAsync, ActionError } from '@ansimclean/ui';
import { getManagerNotifications, updateManagerNotification } from '../../api/manager.js';
import { NOTIFICATION_TOGGLE_FAILED, PUSH_OS_DENIED_NOTE } from '../../api/mock.js';
import { getPushState, subscribePushState } from '../../push/pushClient.js';

/** 알림 설정 화면. 토글을 누르면 화면을 먼저 바꾸고 서버에 저장한다 — 실패하면 되돌리고 문구를 보인다 */
export default function Notifications() {
  const navigate = useNavigate();
  const { data, error, reload, refresh, setData } = useAsync(() => getManagerNotifications(), []);
  const [toggleFailed, setToggleFailed] = useState(false); // 마지막 저장이 실패했는가 (다음 토글 때 지운다)
  // useSyncExternalStore: React 밖의 값(pushClient 의 권한 상태)을 구독해, 바뀌면 이 화면을 다시 그린다
  const pushState = useSyncExternalStore(subscribePushState, getPushState, getPushState); // OS 권한 (검증 검토)
  const chains = useRef({}); // 키별 요청 순서 — 같은 토글을 빠르게 두 번 누르면 앞 요청이 끝난 뒤 다음을 보낸다(서버에 도착 순서가 뒤집히지 않게, 검수 3차)
  const navBar = <NavBar title="알림 설정" onBack={() => navigate('/profile')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  // 먼저 화면을 바꾸고(낙관적 갱신) 저장에 실패하면 되돌린다
  const toggle = (key, enabled) => {
    setToggleFailed(false);
    setData((d) => ({ ...d, values: { ...d.values, [key]: enabled } }));
    const send = async () => {
      try {
        await updateManagerNotification(key, enabled);
      } catch {
        setToggleFailed(true);
        setData((d) => ({ ...d, values: { ...d.values, [key]: !enabled } })); // 먼저 되돌리고
        refresh().catch(() => {}); // 서버 값으로 맞춘다 — reload 는 화면 전체를 로딩·오류로 덮어 실패 문구가 안 보였다 (자체 점검 #48)
      }
    };
    // 이 key 의 앞 요청(Promise) 뒤에 이어 붙인다. send 는 실패를 스스로 잡으므로 사슬이 끊기지 않는다
    chains.current[key] = (chains.current[key] ?? Promise.resolve()).then(send);
  };

  return (
    <ScreenLayout navBar={navBar}>
      <ActionError error={toggleFailed ? { message: NOTIFICATION_TOGGLE_FAILED } : null} />
      {/* 푸시 토글이 켜져 있는데 OS 권한이 거부됐거나(denied) OS 가 기기 토큰 발급에 실패했을 때(error)만 안내 */}
      {data.values.push && (pushState === 'denied' || pushState === 'error') && <ActionError error={{ message: PUSH_OS_DENIED_NOTE }} />}
      <MenuGroup title="알림">
        {data.rows.map((r) => (
          <MenuRow key={r.key} label={r.label} checked={Boolean(data.values[r.key])} onToggle={(v) => toggle(r.key, v)} />
        ))}
      </MenuGroup>
    </ScreenLayout>
  );
}
