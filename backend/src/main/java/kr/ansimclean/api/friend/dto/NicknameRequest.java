package kr.ansimclean.api.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /customer/friends/me — 닉네임 정하기 (1~12자, '#' 금지 — ProfileService.setFriendNickname 이 다시 본다) */
public record NicknameRequest(@NotBlank @Size(max = 40) String nickname) {}
