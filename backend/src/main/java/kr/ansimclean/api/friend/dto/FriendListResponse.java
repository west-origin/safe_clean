package kr.ansimclean.api.friend.dto;

import java.util.List;

/** GET /customer/friends — 친구 목록 (탈퇴한 사람 제외) */
public record FriendListResponse(List<FriendEntry> friends) {}
