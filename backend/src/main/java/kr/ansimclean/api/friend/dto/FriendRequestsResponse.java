package kr.ansimclean.api.friend.dto;

import java.util.List;

/** GET /customer/friends/requests?box=received|sent — 대기 중인 요청만, 최근 순 */
public record FriendRequestsResponse(String box, List<FriendEntry> requests) {}
