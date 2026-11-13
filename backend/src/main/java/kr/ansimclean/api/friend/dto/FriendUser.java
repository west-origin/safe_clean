package kr.ansimclean.api.friend.dto;

import java.util.UUID;

/** 친구 화면의 사람 한 명. 전화번호는 내보내지 않는다. handle = "닉네임#1234", maskedName = 이름 가운데를 가린 값(예: 김*수) */
public record FriendUser(UUID userId, String nickname, String tag, String handle, String maskedName) {}
