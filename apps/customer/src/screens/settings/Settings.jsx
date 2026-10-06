// Figma: Settings (2048:1709) — 393×1233 (긴 화면, 스크롤). 헤더 + ProfileCard + MenuGroup 4개 + 로그아웃 + TabBar(Settings)
// 메뉴 행의 연결 화면은 Figma 에 없음 → 코드에서 정함(아래 MENU_PATHS). 로그아웃 → /signup (PROGRESS.md 판단 항목)
// 설정 탭(/settings). 헤더·프로필·메뉴 그룹·알림 설정 값은 서버(GET /customer/settings)가 주고, 이 화면은 그대로 그린다.
// 하는 일: 메뉴 행 → 아래 MENU_PATHS 의 하위 화면, 알림 토글 저장(낙관적 갱신), 로그아웃, 회원 탈퇴(비밀번호 확인 시트).
import { useRef, useState, useSyncExternalStore } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, TabBar, ProfileCard, MenuGroup, MenuRow, DeleteAccountSheet, cx, useAsync, useAction, ErrorState, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getSettings, updateNotificationSetting, logout, deleteAccount } from '../../api/customer.js';
import { useCustomerTabs } from '../useCustomerTabs.js';
import { NOTIFICATION_TOGGLE_FAILED, PUSH_OS_DENIED_NOTE } from '../../api/mock.js';
import { getPushState, subscribePushState } from '../../push/pushClient.js';
import styles from './Settings.module.css';

/** 메뉴 key → 화면 (Figma 에 연결 없음 → 코드에서 정함). 약관 2개는 가입 약관 본문 화면을 같이 쓴다 */
const MENU_PATHS = {
  editProfile: '/settings/profile',
  addresses: '/settings/addresses',
  payment: '/settings/payment',
  faq: '/settings/faq',
  inquiry: '/settings/inquiry',
  notice: '/settings/notices',
  terms: '/terms/service',
  privacy: '/terms/privacy',
};

/** 설정 탭 화면. 조회가 실패해도 로그아웃 버튼은 남긴다 */
export default function Settings() {
  const navigate = useNavigate();
  const selectTab = useCustomerTabs(); // 하단 탭을 누르면 그 탭 경로로 이동하는 함수
  const { data, error, reload, refresh, setData } = useAsync(() => getSettings(), []);


  // 먼저 화면을 바꾸고(낙관적 갱신) 저장에 실패하면 원래대로 되돌린다
  const [toggleFailed, setToggleFailed] = useState(false);
  // useSyncExternalStore: React 밖(pushClient.js)의 값을 구독해, 바뀌면 이 화면을 다시 그린다.
  // 값은 unknown(아직 모름·웹) / granted / denied / error(등록 실패)
  const pushState = useSyncExternalStore(subscribePushState, getPushState, getPushState); // OS 권한 (검증 검토)
  const chains = useRef({}); // 키별 요청 순서 — 같은 토글을 빠르게 두 번 누르면 앞 요청이 끝난 뒤 다음을 보낸다(서버 도착 순서가 뒤집히지 않게, 검수 3차)
  // 알림 토글(key = push·marketing 등 메뉴 행의 key): 화면 값을 먼저 바꾸고, 서버 저장(PUT /customer/notifications/:key)은
  // 같은 키의 앞 요청 뒤에 줄 세워 보낸다. 실패하면 되돌리고 실패 문구를 띄운 뒤 서버 값으로 다시 맞춘다
  const toggle = (key, enabled) => {
    setToggleFailed(false);
    setData((d) => ({ ...d, notifications: { ...d.notifications, [key]: enabled } }));
    const send = async () => {
      try {
        await updateNotificationSetting(key, enabled);
      } catch {
        setToggleFailed(true);
        setData((d) => ({ ...d, notifications: { ...d.notifications, [key]: !enabled } })); // 먼저 되돌리고
        refresh().catch(() => {}); // 서버 값으로 맞춘다 — reload 는 화면 전체를 로딩·오류로 덮어 실패 문구가 안 보였다 (자체 점검 #48)
      }
    };
    chains.current[key] = (chains.current[key] ?? Promise.resolve()).then(send); // send 는 실패를 스스로 삼키므로 줄이 끊기지 않는다
  };

  // 로그아웃: 푸시 토큰 삭제·서버 로그아웃 요청 뒤 이 기기의 세션을 지우고 가입 화면(/signup)으로
  const signOut = useAction(async () => {
    await logout(); // 서버 폐기는 짧게(5초) 기다리고 세션은 반드시 지운다
    navigate('/signup');
  });
  // 회원 탈퇴 (Figma 에 없음 — 코드에서 정함, 앱스토어 요건). 비밀번호 확인 시트 → 탈퇴 → 가입 화면
  const [deleteOpen, setDeleteOpen] = useState(false);
  // 탈퇴 실행: 시트가 넘겨준 비밀번호로 DELETE /auth/account. 성공하면 세션이 지워지고 기록을 바꿔(replace) 가입 화면으로
  const remove = useAction(async (password) => {
    await deleteAccount(password);
    navigate('/signup', { replace: true });
  });
  const closeDelete = () => {
    setDeleteOpen(false);
    remove.reset(); // 다시 열 때 이전 오류 문구가 남지 않게 (비밀번호는 시트가 스스로 비운다)
  };

  if (error) {
    // 설정 조회가 계속 실패해도 로그아웃 길은 남긴다 (검수 3차)
    return (
      <ScreenLayout tabBar={<TabBar active="settings" onSelect={selectTab} />}>
        <ErrorState onRetry={reload} />
        <div className={styles.logoutRow}>
          <button type="button" className={cx('text-body-default', styles.logout)} disabled={signOut.loading} onClick={signOut.run}>
            {signOut.loading ? '로그아웃 중…' : '로그아웃'}
          </button>
        </div>
      </ScreenLayout>
    );
  }
  if (!data) return <ScreenLoading tabBar={<TabBar active="settings" onSelect={selectTab} />} />;

  return (
    <ScreenLayout noPadding gap="none" tabBar={<TabBar active="settings" onSelect={selectTab} />}>
      <div className={styles.body}>
        <header className={cx(styles.inset, styles.header)}>
          <h1 className={cx('text-heading-m', styles.title)}>{data.header.title}</h1>
          <p className={cx('text-body-s', styles.description)}>{data.header.description}</p>
        </header>

        {data.profile && (
          <div className={styles.inset}>
            <ProfileCard name={data.profile.name} phone={data.profile.phone} avatarSrc={data.profile.avatarSrc || undefined} onClick={() => navigate('/settings/profile')} />
          </div>
        )}

        {/* 알림 토글 저장 실패 문구, 그리고 푸시를 켜 두었는데 OS 권한이 꺼졌거나 등록이 실패했을 때의 안내.
            ActionError 에 { message } 객체를 직접 넘겨 같은 모양의 한 줄 문구로 그린다 */}
        <ActionError error={toggleFailed ? { message: NOTIFICATION_TOGGLE_FAILED } : null} className={styles.toggleError} />
        {data.notifications.push && (pushState === 'denied' || pushState === 'error') && <ActionError error={{ message: PUSH_OS_DENIED_NOTE }} className={styles.toggleError} />}
        {data.groups.map((g) => (
          <div key={g.title} className={styles.inset}>
            <MenuGroup title={g.title}>
              {/* 행 모양은 서버가 준 행에 따라 셋: toggle 이 있으면 스위치, value 가 있으면 값만 보이는 행, 그 밖은 누르면 이동하는 행.
                  MENU_PATHS 에 없는 key 는 설정 화면 그대로(/settings) */}
              {g.rows.map((row) =>
                row.toggle ? (
                  <MenuRow key={row.key} label={row.label} checked={Boolean(data.notifications[row.key])} onToggle={(v) => toggle(row.key, v)} />
                ) : row.value !== undefined ? (
                  <MenuRow key={row.key} label={row.label} value={row.key === 'version' ? `v${import.meta.env.VITE_APP_VERSION ?? row.value}` : row.value} /> // 버전은 빌드 값
                ) : (
                  <MenuRow key={row.key} label={row.label} onClick={() => navigate(MENU_PATHS[row.key] ?? '/settings')} />
                ),
              )}
            </MenuGroup>
          </div>
        ))}

        <div className={styles.logoutRow}>
          <button type="button" className={cx('text-body-default', styles.logout)} disabled={signOut.loading} onClick={signOut.run}>
            {signOut.loading ? '로그아웃 중…' : '로그아웃'}
          </button>
          <button type="button" className={cx('text-body-default', styles.logout)} onClick={() => setDeleteOpen(true)}>
            회원 탈퇴
          </button>
        </div>
      </div>
      {/* 회원 탈퇴 비밀번호 확인 시트. '탈퇴하기' 를 누르면 입력한 비밀번호로 remove.run 을 부른다 */}
      <DeleteAccountSheet open={deleteOpen} onClose={closeDelete} onConfirm={remove.run} loading={remove.loading} error={remove.error?.message} />
    </ScreenLayout>
  );
}
