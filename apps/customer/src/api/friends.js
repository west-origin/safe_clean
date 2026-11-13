// 고객 친구 API 어댑터 (backend/docs/api.md '고객 친구'). 친구 화면(screens/friends)은 이 함수들만 부른다.
// 응답 모양은 서버 그대로: FriendUser = { userId, nickname, tag, handle: "닉네임#1234", maskedName }, FriendEntry = { user, requestId, at, atLabel }. (token-ok: 친구 코드 — 색이 아니다)
// 쓰기 중 요청 보내기·초대 수락은 멱등 키를 받는다 — 화면이 한 번 만든 키를 다시 누를 때도 같이 보내 같은 요청이 두 번 저장되지 않게.
import { http } from './http.js';

const id = (v) => encodeURIComponent(v);

/** 내 닉네임#태그 → { nickname, tag, handle, needsNickname } */
export const getMyFriendProfile = () => http.get('/customer/friends/me');
/** 닉네임 정하기 (1~12자, # 금지) — 태그는 서버가 붙인다 */
export const setFriendNickname = (nickname) => http.put('/customer/friends/me', { nickname });
/** 닉네임#태그 정확히 일치 → { user: FriendUser|null, relation: none|friend|sent|received|self|null } */
export const searchFriend = (q) => http.get(`/customer/friends/search?q=${id(q)}`);
/** 친구 목록 → { friends: FriendEntry[] } */
export const getFriends = () => http.get('/customer/friends');
/** 친구 삭제 (양쪽 해제, 알림 없음) */
export const removeFriend = (userId) => http.delete(`/customer/friends/${id(userId)}`);
/** box = received | sent → { box, requests: FriendEntry[] } (대기 중만) */
export const getFriendRequests = (box) => http.get(`/customer/friends/requests?box=${id(box)}`);
/** 친구 요청 → { id, status: pending|accepted, user } — 상대가 먼저 보낸 요청이 있으면 바로 accepted */
export const sendFriendRequest = (userId, idempotencyKey) => http.post('/customer/friends/requests', { userId }, { idempotencyKey });
export const acceptFriendRequest = (requestId) => http.post(`/customer/friends/requests/${id(requestId)}/accept`);
export const rejectFriendRequest = (requestId) => http.post(`/customer/friends/requests/${id(requestId)}/reject`);
export const cancelFriendRequest = (requestId) => http.post(`/customer/friends/requests/${id(requestId)}/cancel`);
/** 차단 목록 → { blocks: FriendEntry[] } */
export const getBlockedFriends = () => http.get('/customer/friends/blocks');
/** 차단 — 친구 해제 + 대기 요청 취소 */
export const blockUser = (userId) => http.post('/customer/friends/blocks', { userId });
export const unblockUser = (userId) => http.delete(`/customer/friends/blocks/${id(userId)}`);
/** 공유할 초대 링크 → { code, url, expiresAt } */
export const createFriendInvite = () => http.post('/customer/friends/invites');
/** 초대 링크로 들어왔을 때 → { inviter: FriendUser, relation } (만료·차단이면 404) */
export const getFriendInvite = (code) => http.get(`/customer/friends/invites/${id(code)}`);
/** 초대 수락 → 바로 친구 { id, status: accepted, user } */
export const acceptFriendInvite = (code, idempotencyKey) => http.post(`/customer/friends/invites/${id(code)}/accept`, {}, { idempotencyKey });
