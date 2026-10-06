package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /customer/notifications/{key} */
// 매니저 앱의 PUT /manager/notifications/{key} 도 같은 모양을 쓴다. 켤지 끌지 { enabled: true|false }.
// Boolean(객체)이라 enabled 가 빠지거나 null 이면 @NotNull 에 걸려 400 이 난다
public record NotificationRequest(@NotNull Boolean enabled) {}
