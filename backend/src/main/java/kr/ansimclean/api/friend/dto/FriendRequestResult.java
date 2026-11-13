package kr.ansimclean.api.friend.dto;

import java.util.UUID;

/** 친구 요청 보내기·수락·거절·취소·초대 수락의 결과. status: pending | accepted | rejected | cancelled (accepted 면 이제 친구) */
public record FriendRequestResult(UUID id, String status, FriendUser user) {}
