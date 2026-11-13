// 친구 (코드에서 정함, 2026-11-13 — Figma 에 화면 없음. 들어오는 길: 설정 › 친구·선물 › 친구, 친구 요청 푸시)
// 내 친구 코드(닉네임#태그) + 카카오톡 초대 + 친구 찾기, 탭 3개(친구 / 받은 요청 / 보낸 요청), 차단한 사람.
// 닉네임이 없는 고객(소셜 가입)은 NicknameForm 을 먼저 보여 준다. 문구는 mock.js FRIENDS_COPY, 데이터는 api/friends.js.
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import {
  ScreenLayout, NavBar, Card, Button, UnderlineTabs, PersonRow, EmptyState, MenuGroup, MenuRow, Sheet, ScreenError, ScreenLoading,
  ActionError, useAsync, useAction, showToast,
} from '@ansimclean/ui';
import {
  getMyFriendProfile, getFriends, getFriendRequests, acceptFriendRequest, rejectFriendRequest, cancelFriendRequest,
  removeFriend, blockUser, createFriendInvite,
} from '../../api/friends.js';
import { FRIENDS_COPY as C } from '../../api/mock.js';
import NicknameForm from './NicknameForm.jsx';
import { shareInvite } from './shareInvite.js';
import styles from './friends.module.css';

const TABS = ['friends', 'received', 'sent'];

/** 친구 화면 */
export default function Friends() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = TABS.includes(params.get('tab')) ? params.get('tab') : 'friends';
  const { data, error, reload } = useAsync(async () => {
    const me = await getMyFriendProfile();
    if (me.needsNickname) return { me };
    const [friends, received, sent] = await Promise.all([getFriends(), getFriendRequests('received'), getFriendRequests('sent')]);
    return { me, friends: friends.friends, received: received.requests, sent: sent.requests };
  }, []);
  const [manage, setManage] = useState(null); // 관리 시트를 연 친구 (FriendEntry)

  // 줄 버튼 동작: 성공하면 목록을 다시 받는다(서버가 자동 친구 등으로 상태를 바꿨을 수 있어서)
  const act = useAction(async (fn, done) => {
    await fn();
    if (done) showToast(done);
    setManage(null);
    reload();
  });
  const invite = useAction(async () => {
    const link = await createFriendInvite();
    const result = await shareInvite({ title: C.inviteShareTitle, text: C.inviteShareText(data.me.handle), url: link.url });
    if (result === 'copied') showToast(C.inviteCopied);
  });

  const navBar = <NavBar title={C.title} onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  if (data.me.needsNickname) {
    return (
      <ScreenLayout navBar={navBar}>
        <NicknameForm onSaved={() => reload()} />
      </ScreenLayout>
    );
  }

  const counts = { friends: data.friends.length, received: data.received.length, sent: data.sent.length };
  const options = TABS.map((key) => ({ key, label: counts[key] ? `${C.tabs[key]} ${counts[key]}` : C.tabs[key] }));
  const rows = data[tab];
  const subtitle = (e) => [e.user.maskedName, e.atLabel].filter(Boolean).join(' · ');
  const actionsFor = (e) => {
    if (tab === 'friends') return [{ label: C.manage, onClick: () => setManage(e) }];
    if (tab === 'received') {
      return [
        { label: C.accept, tone: 'primary', disabled: act.loading, onClick: () => act.run(() => acceptFriendRequest(e.requestId), C.becameFriends) },
        { label: C.reject, disabled: act.loading, onClick: () => act.run(() => rejectFriendRequest(e.requestId)) },
      ];
    }
    return [{ label: C.cancel, disabled: act.loading, onClick: () => act.run(() => cancelFriendRequest(e.requestId)) }];
  };

  return (
    <ScreenLayout navBar={navBar} gap="md">
      <Card tone="subtle" gap="sm">
        <p className={`text-caption-l ${styles.plain}`}>{C.myCodeLabel}</p>
        <p className={`text-heading-s ${styles.plain}`}>{data.me.handle}</p>
        <p className={`text-body-s ${styles.plain}`}>{C.myCodeHint}</p>
        <div className={styles.column}>
          <Button chevron={false} loading={invite.loading} onClick={invite.run}>{C.invite}</Button>
          <Button variant="outline" chevron={false} onClick={() => navigate('/friends/search')}>{C.find}</Button>
        </div>
        <ActionError error={invite.error} fallback={C.inviteFailed} />
      </Card>

      <UnderlineTabs options={options} value={tab} onChange={(key) => setParams({ tab: key }, { replace: true })} ariaLabel={C.title} />
      <ActionError error={act.error} fallback={C.actionFailed} />
      {rows.length === 0 ? (
        <EmptyState title={C.empty[tab].title} description={C.empty[tab].description} />
      ) : (
        <div className={styles.list}>
          {rows.map((e) => (
            <PersonRow key={e.requestId ?? e.user.userId} title={e.user.handle} subtitle={subtitle(e)} actions={actionsFor(e)} />
          ))}
        </div>
      )}

      <MenuGroup title={C.manageGroup}>
        <MenuRow label={C.blocksTitle} onClick={() => navigate('/friends/blocks')} />
      </MenuGroup>

      {/* 친구 관리 시트: 삭제(알림 없음) · 차단 */}
      <Sheet open={Boolean(manage)} onClose={() => { setManage(null); act.reset(); }} title={manage ? C.manageSheetTitle(manage.user.handle) : undefined}>
        {manage && (
          <div className={styles.stack}>
            <ActionError error={act.error} fallback={C.actionFailed} />
            <Button variant="outline" chevron={false} loading={act.loading} onClick={() => act.run(() => removeFriend(manage.user.userId), C.removeDone)}>{C.remove}</Button>
            <Button variant="destructive" chevron={false} loading={act.loading} onClick={() => act.run(() => blockUser(manage.user.userId), C.blockDone)}>{C.block}</Button>
            <Button variant="outlineGray" chevron={false} onClick={() => setManage(null)}>{C.close}</Button>
          </div>
        )}
      </Sheet>
    </ScreenLayout>
  );
}
