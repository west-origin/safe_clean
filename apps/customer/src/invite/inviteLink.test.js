// 초대 링크 해석·기억 (invite/inviteLink.js)
import { describe, it, expect, beforeEach } from 'vitest';
import { inviteCodeOf, openInviteUrl, takePendingInvite, rememberInvite } from './inviteLink.js';

beforeEach(() => localStorage.clear());

describe('inviteLink', () => {
  it('앱 링크·웹 링크·코드만 모두 읽고 그 밖은 버린다', () => {
    expect(inviteCodeOf('ansimclean://friend-invite/Abcdef2345')).toBe('Abcdef2345');
    expect(inviteCodeOf('https://api.test/i/Abcdef2345')).toBe('Abcdef2345');
    expect(inviteCodeOf(' Abcdef2345 ')).toBe('Abcdef2345');
    expect(inviteCodeOf('ansimclean://auth/callback?code=x')).toBeNull();
    expect(inviteCodeOf('https://api.test/i/short')).toBeNull();
  });

  it('앱이 초대 링크로 열리면 코드를 기억하고 한 번만 꺼낸다', () => {
    expect(openInviteUrl('ansimclean://auth/callback')).toBeNull();
    expect(openInviteUrl('ansimclean://friend-invite/Abcdef2345')).toBe('/friends/invite/Abcdef2345');
    expect(takePendingInvite()).toBe('Abcdef2345');
    expect(takePendingInvite()).toBeNull();
    rememberInvite('bad');
    expect(takePendingInvite()).toBeNull();
  });
});
