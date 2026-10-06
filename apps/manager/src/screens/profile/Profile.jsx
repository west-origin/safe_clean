// Figma: Manager/Profile (2141:2820)
// 프로토타입: 근무 일정 관리 → Schedule/Availability · 촬영 장비 → Profile/Equipment · 탭 → 각 탭 화면. 나머지 메뉴는 연결 없음 (TODO(figma))
// 경로 /profile (하단 탭 MY). 데이터 { name, stats, menu } 는 GET /manager/profile — 메뉴 목록도 서버가 준다.
// 화면: 이름·통계 카드 → 메뉴(각 하위 화면으로) → 로그아웃 · 회원 탈퇴 버튼과 탈퇴 확인 시트.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, ManagerTabBar, Card, ListItem, DeleteAccountSheet, cx, useAsync, useAction, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getProfile, logout, deleteAccount } from '../../api/manager.js';
import { useManagerTabs } from '../useManagerTabs.js';
import styles from './Profile.module.css';

/** 메뉴 key → 화면. 프로토타입 연결이 있는 것(to)은 mock 에, 없던 것은 여기서 정함 */
// 실서버에서는 메뉴와 to 가 app_settings.manager_profile_menu 에서 온다 (mock 과 같은 값)
const MENU_PATHS = {
  info: '/profile/info',
  area: '/profile/area',
  identity: '/profile/identity',
  education: '/profile/education',
  account: '/profile/account',
  penalties: '/profile/penalties',
  notifications: '/profile/notifications',
  notice: '/profile/notices',
  support: '/profile/support',
};

/** MY 탭 화면. 로그아웃·탈퇴가 끝나면 로그인 화면으로 간다 (replace: 방문 기록의 지금 칸을 바꿔 써서 뒤로가기로 이 화면에 돌아오지 않게) */
export default function Profile() {
  const navigate = useNavigate();
  const goTab = useManagerTabs(); // 하단 탭을 누르면 그 탭 경로로 이동하는 함수
  const { data: data, error, reload } = useAsync(() => getProfile(), []);
  const [deleteOpen, setDeleteOpen] = useState(false); // 탈퇴 확인 시트가 열려 있는가
  // 회원 탈퇴: 시트에서 받은 비밀번호로 DELETE /auth/account. 성공하면 api 가 세션을 지우고 여기서 로그인 화면으로.
  // 실패(비밀번호 불일치, 맡은 작업·미지급 정산이 남음 등)하면 그 오류 문구(remove.error.message)가 시트 안에 보인다
  const remove = useAction(async (password) => {
    await deleteAccount(password);
    navigate('/auth/login', { replace: true });
  });


  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<ManagerTabBar active="my" onSelect={goTab} />} />;
  if (!data) return <ScreenLoading tabBar={<ManagerTabBar active="my" onSelect={goTab} />} />;

  return (
    <ScreenLayout tabBar={<ManagerTabBar active="my" onSelect={goTab} />}>
      <h1 className="text-heading-s">MY</h1>

      <Card tone="muted" gap="md">
        <p className="text-heading-s">{data.name}</p>
        <dl className={styles.stats}>
          {data.stats.map((s) => (
            <div key={s.label} className={styles.stat}>
              <dt className={cx('text-body-s', styles.secondary)}>{s.label}</dt>
              <dd className="text-heading-s">{s.value}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <Card padding="list" gap="none" divided>
        {/* 메뉴 항목에 to 가 있으면 그 경로, 없으면 위 MENU_PATHS, 둘 다 없으면 MY 그대로 */}
        {data.menu.map((item) => (
          <ListItem key={item.key} label={item.label} onClick={() => navigate(item.to ?? MENU_PATHS[item.key] ?? '/profile')} />
        ))}
      </Card>

      {/* 로그아웃: Figma 에 없음 (코드에서 정함). 로그인 화면이 생기면서 필요해졌다 → /auth/login */}
      {/* logout() 은 서버 요청(푸시 토큰 삭제·로그아웃)이 실패해도 던지지 않고 세션을 지운다 — 이어서 로그인 화면으로 */}
      <button
        type="button"
        className={cx('text-body-s', styles.logout)}
        onClick={async () => {
          await logout();
          navigate('/auth/login', { replace: true });
        }}
      >
        로그아웃
      </button>
      {/* 회원 탈퇴: 비밀번호 확인 시트(DeleteAccountSheet)를 연다. 확인을 누르면 remove.run(비밀번호) */}
      <button type="button" className={cx('text-body-s', styles.logout)} onClick={() => setDeleteOpen(true)}>
        회원 탈퇴
      </button>
      <DeleteAccountSheet
        open={deleteOpen}
        onClose={() => {
          setDeleteOpen(false);
          remove.reset(); // 다시 열 때 이전 오류 문구가 남지 않게
        }}
        onConfirm={remove.run}
        loading={remove.loading}
        error={remove.error?.message}
        description="탈퇴하면 계정이 삭제되고 되돌릴 수 없어요. 완료한 청소와 정산 내역은 법령에 따라 일정 기간 보관돼요."
      />
    </ScreenLayout>
  );
}
