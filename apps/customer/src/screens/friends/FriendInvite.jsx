// 친구 초대 받기 (코드에서 정함, 2026-11-13 — 들어오는 길: 카카오톡 초대 링크 ansimclean://friend-invite/{code} · 찾기 화면의 초대 코드 입력 ·
// 로그인 전에 링크를 열었으면 가입·로그인 뒤 홈이 이리로 보낸다). 수락하면 바로 친구, '나중에' 는 아무것도 저장하지 않는다.
import { useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Button, PersonRow, EmptyState, ScreenLoading, ScreenError, ActionError, useAsync, useAction, showToast } from '@ansimclean/ui';
import { getFriendInvite, getMyFriendProfile, acceptFriendInvite } from '../../api/friends.js';
import { newIdempotencyKey } from '../../api/http.js';
import { FRIENDS_COPY as C } from '../../api/mock.js';
import { clearPendingInvite } from '../../invite/inviteLink.js';
import NicknameForm from './NicknameForm.jsx';
import styles from './friends.module.css';

/** 초대 받기 화면 */
export default function FriendInvite() {
  const { code } = useParams();
  const navigate = useNavigate();
  useEffect(() => clearPendingInvite(), []); // 이 화면에 왔으면 기억해 둔 초대는 다 썼다
  const { data, error, reload } = useAsync(async () => {
    const me = await getMyFriendProfile();
    try {
      return { me, invite: await getFriendInvite(code) };
    } catch (e) {
      if (e?.status === 404) return { me, invite: null }; // 만료·없음·차단 — 같은 안내
      throw e;
    }
  }, [code]);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- 초대 코드가 바뀌면 새 멱등 키
  const key = useMemo(() => newIdempotencyKey(), [code]);
  const accept = useAction(async () => {
    await acceptFriendInvite(code, key);
    showToast(C.becameFriends);
    navigate('/friends', { replace: true });
  });

  const navBar = <NavBar title={C.inviteTitle} onBack={() => navigate('/friends')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  if (!data.invite) {
    return (
      <ScreenLayout navBar={navBar} stickyCta={<Button chevron={false} onClick={() => navigate('/friends')}>{C.goFriends}</Button>}>
        <EmptyState title={C.inviteExpired.title} description={C.inviteExpired.description} />
      </ScreenLayout>
    );
  }
  const { inviter, relation } = data.invite;
  if (data.me.needsNickname && relation !== 'self' && relation !== 'friend') {
    return (
      <ScreenLayout navBar={navBar}>
        <NicknameForm onSaved={() => reload()} />
      </ScreenLayout>
    );
  }
  const done = relation === 'self' || relation === 'friend';
  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        done ? (
          <Button chevron={false} onClick={() => navigate('/friends', { replace: true })}>{C.goFriends}</Button>
        ) : (
          <div className={styles.buttons}>
            <Button variant="outlineGray" chevron={false} onClick={() => navigate('/friends', { replace: true })}>{C.inviteLater}</Button>
            <Button chevron={false} loading={accept.loading} onClick={accept.run}>{C.inviteAccept}</Button>
          </div>
        )
      }
    >
      <h1 className={`text-heading-m ${styles.headline}`}>{C.inviteHeadline(inviter.nickname)}</h1>
      <PersonRow title={inviter.handle} subtitle={inviter.maskedName} />
      {relation === 'self' && <p className="text-body-default">{C.inviteSelf}</p>}
      {relation === 'friend' && <p className="text-body-default">{C.inviteAlreadyFriend}</p>}
      <ActionError error={accept.error} fallback={C.actionFailed} />
    </ScreenLayout>
  );
}
