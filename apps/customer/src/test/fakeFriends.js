// 가짜 서버의 친구 부분 (test/fakeHttp.js 가 합친다). 서버 규칙 중 화면이 의존하는 것만 흉내 낸다:
// 닉네임#태그 정확히 일치 검색, 상대가 먼저 보낸 요청이 있으면 바로 친구, 받은 사람만 수락·거절, 차단하면 친구 해제·요청 취소,
// 초대 코드 'Invite0001' = f-4 의 초대, 'Expired001' = 만료(404).
import { ApiError } from './fakeHttp.js';
import { FRIEND_ME, FRIEND_PEOPLE } from '../api/mock.js';

export const FAKE_INVITE_CODE = 'Invite0001';
export const EXPIRED_INVITE_CODE = 'Expired001';

/** 친구 state 의 처음 모양: 친구 f-1·f-2, 받은 요청 f-3, 보낸 요청 없음, f-4 는 아무 관계 없음 */
export function friendState() {
  return {
    me: { ...FRIEND_ME },
    friends: ['f-1', 'f-2'],
    requests: [{ id: 'q-1', from: 'f-3', to: 'me', status: 'pending', at: '2026-11-13T03:00:00Z' }],
    blocks: [],
  };
}

const person = (id) => FRIEND_PEOPLE.find((p) => p.userId === id);
const entry = (id, requestId = null, at = '2026-11-13T03:00:00Z') => ({ user: person(id), requestId, at, atLabel: '11월 13일' });
const notFound = () => new ApiError(404, 'NOT_FOUND', '찾을 수 없어요');

/** fakeHttp 의 exact·patterns 에 더할 처리 함수들 (s = state.friend) */
export function friendRoutes(state) {
  const s = () => state.friend;
  const pendingBetween = (a) => s().requests.find((r) => r.status === 'pending' && (r.from === a || r.to === a));
  const relation = (id) => {
    if (s().friends.includes(id)) return 'friend';
    const r = pendingBetween(id);
    if (!r) return 'none';
    return r.from === 'me' ? 'sent' : 'received';
  };
  const requireNickname = () => {
    if (s().me.needsNickname) throw new ApiError(409, 'INVALID_STATE', '친구 기능을 쓰려면 닉네임을 먼저 정해주세요');
  };
  const sendTo = (id) => {
    requireNickname();
    if (!person(id) || s().blocks.includes(id)) throw notFound();
    if (s().friends.includes(id)) throw new ApiError(409, 'INVALID_STATE', '이미 친구예요');
    const r = pendingBetween(id);
    if (r && r.from === id) {
      r.status = 'accepted';
      s().friends.push(id);
      return { id: r.id, status: 'accepted', user: person(id) };
    }
    if (r) return { id: r.id, status: 'pending', user: person(id) };
    const created = { id: `q-${s().requests.length + 1}`, from: 'me', to: id, status: 'pending', at: '2026-11-13T04:00:00Z' };
    s().requests.push(created);
    return { id: created.id, status: 'pending', user: person(id) };
  };
  const decide = (requestId, status, asAddressee) => {
    const r = s().requests.find((x) => x.id === requestId);
    if (!r || (asAddressee ? r.to !== 'me' : r.from !== 'me')) throw notFound();
    if (r.status !== 'pending' && r.status !== status) throw new ApiError(409, 'INVALID_STATE', '이미 처리된 요청이에요');
    r.status = status;
    const other = asAddressee ? r.from : r.to;
    if (status === 'accepted' && !s().friends.includes(other)) s().friends.push(other);
    return { id: r.id, status, user: person(other) };
  };
  const inviter = (code) => {
    if (code !== FAKE_INVITE_CODE) throw notFound();
    if (s().blocks.includes('f-4')) throw notFound();
    return 'f-4';
  };

  const exact = {
    'GET /customer/friends/me': () => ({ ...s().me }),
    'PUT /customer/friends/me': (b) => {
      const nickname = String(b.nickname ?? '').trim();
      if (!nickname || nickname.length > 12 || nickname.includes('#')) throw new ApiError(400, 'VALIDATION', '닉네임은 1~12자로 정해주세요');
      s().me = { nickname, tag: '0123', handle: `${nickname}#0123`, needsNickname: false }; // token-ok: 친구 코드 닉네임#태그 — 색이 아니다
      return { ...s().me };
    },
    'GET /customer/friends': () => ({ friends: s().friends.map((id) => entry(id)) }),
    'POST /customer/friends/requests': (b) => sendTo(b.userId),
    'GET /customer/friends/blocks': () => ({ blocks: s().blocks.map((id) => entry(id)) }),
    'POST /customer/friends/blocks': (b) => {
      const id = b.userId;
      s().friends = s().friends.filter((x) => x !== id);
      s().requests.filter((r) => r.status === 'pending' && (r.from === id || r.to === id)).forEach((r) => (r.status = 'cancelled'));
      if (!s().blocks.includes(id)) s().blocks.push(id);
      return { ok: true };
    },
    'POST /customer/friends/invites': () => {
      requireNickname();
      return { code: 'MyCode0001', url: 'https://api.test/i/MyCode0001', expiresAt: '2026-12-13T03:00:00Z' };
    },
  };
  const patterns = [
    ['GET', /^\/customer\/friends\/search\?q=(.+)$/, ([q]) => {
      const text = decodeURIComponent(q).trim();
      const m = text.match(/^(.+)#([0-9]{4})$/);
      if (!m) throw new ApiError(400, 'VALIDATION', '닉네임#숫자4자리 형식으로 입력해주세요');
      if (text === s().me.handle) return { user: { userId: 'me', nickname: s().me.nickname, tag: s().me.tag, handle: s().me.handle, maskedName: '김*늘' }, relation: 'self' };
      const p = FRIEND_PEOPLE.find((x) => x.handle.toLowerCase() === text.toLowerCase());
      if (!p || s().blocks.includes(p.userId)) return { user: null, relation: null };
      return { user: p, relation: relation(p.userId) };
    }],
    ['GET', /^\/customer\/friends\/requests\?box=(received|sent)$/, ([box]) => ({
      box,
      requests: s().requests.filter((r) => r.status === 'pending' && (box === 'received' ? r.to === 'me' : r.from === 'me'))
        .map((r) => entry(box === 'received' ? r.from : r.to, r.id, r.at)),
    })],
    ['POST', /^\/customer\/friends\/requests\/([^/]+)\/accept$/, ([id]) => decide(id, 'accepted', true)],
    ['POST', /^\/customer\/friends\/requests\/([^/]+)\/reject$/, ([id]) => decide(id, 'rejected', true)],
    ['POST', /^\/customer\/friends\/requests\/([^/]+)\/cancel$/, ([id]) => decide(id, 'cancelled', false)],
    ['DELETE', /^\/customer\/friends\/blocks\/([^/]+)$/, ([id]) => {
      s().blocks = s().blocks.filter((x) => x !== id);
      return { ok: true };
    }],
    ['GET', /^\/customer\/friends\/invites\/([^/]+)$/, ([code]) => {
      const id = inviter(code);
      return { inviter: person(id), relation: relation(id) };
    }],
    ['POST', /^\/customer\/friends\/invites\/([^/]+)\/accept$/, ([code]) => {
      requireNickname();
      const id = inviter(code);
      if (!s().friends.includes(id)) s().friends.push(id);
      return { id: 'q-invite', status: 'accepted', user: person(id) };
    }],
    ['DELETE', /^\/customer\/friends\/([^/]+)$/, ([id]) => {
      s().friends = s().friends.filter((x) => x !== id);
      return { ok: true };
    }],
  ];
  return { exact, patterns };
}
