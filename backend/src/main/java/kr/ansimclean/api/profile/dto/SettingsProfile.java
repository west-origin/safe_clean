package kr.ansimclean.api.profile.dto;

/** Settings › ProfileCard — 앱 PROFILE 모양 ("김하늘 님", "010-1234-5678"). avatarSrc 는 서명 URL 자리(B6 계획)지만 지금은 항상 "" */
// 고객 설정 화면 응답(CustomerScreenService)에 들어가는 프로필 카드 한 칸. ProfileService.settingsProfile 이 만든다
public record SettingsProfile(String name, String phone, String avatarSrc) {}
