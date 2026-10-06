package kr.ansimclean.api.profile.dto;

// 알림 토글 저장 응답 { key, enabled } — 고객·매니저 PUT …/notifications/{key} 가 저장한 값을 그대로 돌려준다
public record NotificationResponse(String key, boolean enabled) {}
