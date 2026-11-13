package kr.ansimclean.api.friend.dto;

import java.util.List;

/** GET /customer/friends/blocks — 내가 차단한 사람 */
public record FriendBlocksResponse(List<FriendEntry> blocks) {}
