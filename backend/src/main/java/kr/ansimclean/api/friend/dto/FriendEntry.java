package kr.ansimclean.api.friend.dto;

import java.util.UUID;

/** 목록 한 줄 — 친구(at = 친구가 된 시각) · 요청(requestId, at = 요청 시각) · 차단(at = 차단 시각). at 은 ISO-8601, atLabel 은 "11월 13일" */
public record FriendEntry(FriendUser user, UUID requestId, String at, String atLabel) {}
