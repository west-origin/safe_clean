package kr.ansimclean.api.friend.dto;

/** POST /customer/friends/invites — 공유할 초대 링크. 살아 있는 코드가 있으면 그것을 돌려준다. expiresAt 은 ISO-8601 */
public record InviteResponse(String code, String url, String expiresAt) {}
