package kr.ansimclean.api.friend.dto;

/** GET·PUT /customer/friends/me — 내 닉네임#태그. needsNickname = true 면 친구 기능 전에 닉네임을 정해야 한다 (nickname·tag·handle 은 null) */
public record MyFriendProfileResponse(String nickname, String tag, String handle, boolean needsNickname) {}
