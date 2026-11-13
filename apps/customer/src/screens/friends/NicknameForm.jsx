// 친구 닉네임 정하기 (코드에서 정함, 2026-11-13). 소셜 가입 고객은 닉네임이 없어 친구 기능 전에 정한다.
// 쓰는 곳: Friends(친구 목록)·FriendInvite(초대 받기) — needsNickname 이면 이 폼을 먼저 보여 준다. 문구는 mock.js FRIENDS_COPY.
import { useState } from 'react';
import { TextField, Button, useAction, ActionError } from '@ansimclean/ui';
import { setFriendNickname } from '../../api/friends.js';
import { FRIENDS_COPY as C } from '../../api/mock.js';
import styles from './friends.module.css';

/**
 * @param {object} props
 * @param {(profile: { nickname: string, tag: string, handle: string }) => void} props.onSaved 저장한 뒤 (서버가 붙인 태그 포함)
 */
export default function NicknameForm({ onSaved }) {
  const [nickname, setNickname] = useState('');
  const save = useAction(async () => onSaved(await setFriendNickname(nickname.trim())));
  const tooLong = [...nickname.trim()].length > 12;
  const invalid = !nickname.trim() || tooLong || nickname.includes('#');
  return (
    <div className={styles.stack}>
      <h2 className="text-heading-s">{C.nicknameTitle}</h2>
      <p className={`text-body-s ${styles.plain}`}>{C.nicknameDescription}</p>
      <TextField
        label={C.nicknameLabel}
        placeholder={C.nicknamePlaceholder}
        autoComplete="nickname"
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
        hint={C.nicknamePlaceholder}
        error={tooLong || nickname.includes('#') ? C.nicknamePlaceholder : undefined}
      />
      <ActionError error={save.error} fallback={C.actionFailed} />
      <Button loading={save.loading} disabled={invalid} onClick={save.run}>
        {C.nicknameSave}
      </Button>
    </div>
  );
}
