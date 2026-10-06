package kr.ansimclean.api.profile.dto;

/** GET/PATCH /customer/profile — 앱 PROFILE_FORM 모양. phone 은 앞자리 010 을 뺀 8자리(PhoneField 형식) */
// 설정 › 내 정보 수정 화면의 현재 값. 저장(PATCH) 뒤에도 저장된 값을 다시 읽어 이 모양으로 돌려준다 (ProfileService.profileForm)
public record ProfileFormResponse(String name, String nickname, String phone) {}
