package kr.ansimclean.api.friend.dto;

/** GET /customer/friends/invites/{code} — 초대한 사람과 나의 관계 (relation 은 FriendSearchResponse 와 같은 값) */
public record InvitePreviewResponse(FriendUser inviter, String relation) {}
