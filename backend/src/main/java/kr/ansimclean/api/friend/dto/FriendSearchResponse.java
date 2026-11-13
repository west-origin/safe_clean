package kr.ansimclean.api.friend.dto;

/**
 * GET /customer/friends/search?q=닉네임#1234 — 정확히 일치할 때만 user 가 있다(없거나 차단 관계면 null).
 * relation: none(요청 가능) | friend | sent(내가 보낸 대기 요청) | received(받은 대기 요청) | self(나)
 */
public record FriendSearchResponse(FriendUser user, String relation) {}
