package kr.ansimclean.api.profile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** = 앱 session.user { id, role, name, profileComplete, signupStep } */
// 인증 응답(예: 로그인, GET /auth/me) 안의 user (ProfileService.userResponse 가 만든다).
// profileComplete = 가입 마지막 단계까지 끝났는가, signupStep = 가입 도중이면 다음 화면 이름, 끝났으면 null.
// @JsonInclude(ALWAYS): signupStep 이 null 이어도 키를 빼지 않고 "signupStep": null 로 보낸다
public record UserResponse(UUID id, String role, String name, boolean profileComplete, @JsonInclude(JsonInclude.Include.ALWAYS) String signupStep) {}
