package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /customer/profile. phone 변경은 OTP 재인증이 필요해 지금은 받지 않는다(§13-7) */
// 설정 › 내 정보 수정 저장. 앞뒤 공백은 ProfileService.updateProfile 이 뗀다. nickname 이 빠지면(null) 기존 닉네임을 그대로 둔다
public record UpdateProfileRequest(@NotBlank(message = "이름을 입력해주세요") @Size(max = 50) String name,
                                   @Size(max = 50) String nickname) {}
