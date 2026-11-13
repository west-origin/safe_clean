// 차단한 사람 (코드에서 정함, 2026-11-13 — 들어오는 길: 친구 › 차단한 사람). 차단 해제해도 친구로 돌아오지 않는다(서버 규칙).
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, PersonRow, EmptyState, ScreenError, ScreenLoading, ActionError, useAsync, useAction } from '@ansimclean/ui';
import { getBlockedFriends, unblockUser } from '../../api/friends.js';
import { FRIENDS_COPY as C } from '../../api/mock.js';
import styles from './friends.module.css';

/** 차단 목록 화면 */
export default function FriendBlocks() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getBlockedFriends(), []);
  const unblock = useAction(async (userId) => {
    await unblockUser(userId);
    reload();
  });
  const navBar = <NavBar title={C.blocksTitle} onBack={() => navigate('/friends')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar} gap="md">
      <ActionError error={unblock.error} fallback={C.actionFailed} />
      {data.blocks.length === 0 ? (
        <EmptyState title={C.empty.blocks.title} />
      ) : (
        <div className={styles.list}>
          {data.blocks.map((e) => (
            <PersonRow key={e.user.userId} title={e.user.handle ?? e.user.maskedName} subtitle={e.atLabel}
              actions={[{ label: C.unblock, disabled: unblock.loading, onClick: () => unblock.run(e.user.userId) }]} />
          ))}
        </div>
      )}
    </ScreenLayout>
  );
}
