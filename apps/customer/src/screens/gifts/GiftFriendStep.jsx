// 선물하기 1단계: 받을 친구 고르기 (코드에서 정함 — GiftSend.jsx 가 그린다). 친구 목록에서만 고른다(서버도 친구가 아니면 거부한다).
import { useNavigate } from 'react-router';
import { ScreenLayout, RadioOption, Button, EmptyState } from '@ansimclean/ui';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.navBar
 * @param {{ user: { userId: string, handle: string, maskedName: string } }[]} props.friends 친구 목록 (FriendEntry)
 * @param {string|null} props.selected 고른 친구 userId
 * @param {(userId: string) => void} props.onSelect
 * @param {() => void} props.onNext
 */
export default function GiftFriendStep({ navBar, friends, selected, onSelect, onNext }) {
  const navigate = useNavigate();
  if (friends.length === 0) {
    return (
      <ScreenLayout navBar={navBar}>
        <EmptyState title={C.noFriends.title} description={C.noFriends.description} />
        <Button onClick={() => navigate('/friends')}>{C.goFriends}</Button>
      </ScreenLayout>
    );
  }
  const chosen = friends.some((f) => f.user.userId === selected);
  return (
    <ScreenLayout navBar={navBar} gap="md" stickyCta={<Button disabled={!chosen} onClick={onNext}>{C.next}</Button>}>
      <div className={styles.stack}>
        <h1 className={`text-heading-m ${styles.plain}`}>{C.steps.friend}</h1>
        <p className={`text-body-s ${styles.plain}`}>{C.friendHint}</p>
      </div>
      <div className={styles.list} role="radiogroup" aria-label={C.steps.friend}>
        {friends.map((f) => (
          <RadioOption
            key={f.user.userId}
            label={f.user.handle}
            description={f.user.maskedName}
            selected={f.user.userId === selected}
            onSelect={() => onSelect(f.user.userId)}
          />
        ))}
      </div>
    </ScreenLayout>
  );
}
