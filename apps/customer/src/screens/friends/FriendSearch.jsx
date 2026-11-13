// 친구 찾기 (코드에서 정함, 2026-11-13 — 들어오는 길: 친구 › 친구 찾기)
// ① 친구 코드(닉네임#1234) 정확히 일치 검색 → 결과 한 줄 + 관계에 맞는 버튼(요청 / 요청 보냄 / 수락 / 친구 / 나) (token-ok: 친구 코드 — 색이 아니다)
// ② 초대 코드(10자리)로 추가 → 초대 받기 화면. 미가입 상태로 링크를 받은 친구가 가입 뒤 링크 대신 코드를 넣는 길
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, TextField, Button, PersonRow, EmptyState, SectionDivider, ActionError, useAction, showToast } from '@ansimclean/ui';
import { searchFriend, sendFriendRequest } from '../../api/friends.js';
import { newIdempotencyKey } from '../../api/http.js';
import { FRIENDS_COPY as C } from '../../api/mock.js';
import { inviteCodeOf } from '../../invite/inviteLink.js';
import styles from './friends.module.css';

/** 관계별 버튼 (relation: none | sent | received | friend | self) */
function actionFor(relation, request) {
  switch (relation) {
    case 'none': return { label: C.request, tone: 'primary', onClick: request };
    case 'received': return { label: C.accept, tone: 'primary', onClick: request }; // 서버가 상대의 대기 요청을 보고 바로 친구로
    case 'sent': return { label: C.requested, disabled: true, onClick: () => {} };
    case 'friend': return { label: C.alreadyFriend, disabled: true, onClick: () => {} };
    default: return { label: C.self, disabled: true, onClick: () => {} };
  }
}

/** 친구 찾기 화면 */
export default function FriendSearch() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [result, setResult] = useState(undefined); // undefined = 아직 안 찾음, { user, relation }
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState('');
  // 같은 사람에게 다시 누르면 같은 키, 다른 사람을 찾으면 새 키 (찾은 사람 id 가 바뀔 때마다 새로 만든다)
  const target = result?.user?.userId;
  // eslint-disable-next-line react-hooks/exhaustive-deps -- target 이 바뀔 때 새 키를 만드는 것이 목적이다
  const requestKey = useMemo(() => newIdempotencyKey(), [target]);

  const search = useAction(async () => setResult(await searchFriend(q.trim())));
  const request = useAction(async () => {
    const res = await sendFriendRequest(result.user.userId, requestKey);
    setResult((r) => ({ ...r, relation: res.status === 'accepted' ? 'friend' : 'sent' }));
    showToast(res.status === 'accepted' ? C.becameFriends : C.requestSent);
  });
  const openCode = () => {
    const c = inviteCodeOf(code);
    if (!c) return setCodeError(C.codeInvalid);
    navigate(`/friends/invite/${c}`);
  };

  const action = result?.user ? actionFor(result.relation, request.run) : null;
  return (
    <ScreenLayout navBar={<NavBar title={C.searchTitle} onBack={() => navigate('/friends')} />} gap="md">
      <form className={styles.stack} onSubmit={(e) => { e.preventDefault(); if (q.trim()) search.run(); }}>
        <TextField label={C.searchLabel} placeholder={C.searchPlaceholder} hint={C.searchHint} value={q} onChange={(e) => setQ(e.target.value)} />
        <Button type="submit" chevron={false} loading={search.loading} disabled={!q.includes('#')}>{C.searchButton}</Button>
        <ActionError error={search.error} fallback={C.actionFailed} />
      </form>

      {result !== undefined && (result.user ? (
        <div className={styles.list}>
          <PersonRow title={result.user.handle} subtitle={result.user.maskedName}
            actions={[{ ...action, disabled: action.disabled || request.loading }]} />
          <ActionError error={request.error} fallback={C.actionFailed} />
        </div>
      ) : (
        <EmptyState title={C.searchEmpty.title} description={C.searchEmpty.description} />
      ))}

      <SectionDivider />
      <form className={styles.stack} onSubmit={(e) => { e.preventDefault(); openCode(); }}>
        <h2 className="text-title-card">{C.codeTitle}</h2>
        <TextField label={C.codeLabel} hideLabel placeholder={C.codePlaceholder} value={code} error={codeError || undefined}
          onChange={(e) => { setCode(e.target.value); setCodeError(''); }} />
        <Button type="submit" variant="outline" chevron={false} disabled={!code.trim()}>{C.codeButton}</Button>
      </form>
    </ScreenLayout>
  );
}
