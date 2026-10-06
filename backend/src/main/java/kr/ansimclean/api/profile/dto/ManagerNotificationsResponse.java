package kr.ansimclean.api.profile.dto;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/** GET /manager/notifications — { rows: [{ key, label }], values: { push, call, settlement, marketing } } */
// rows: app_settings.manager_notification_rows 의 JSON 그대로(화면 행 이름·순서), values: 키별 켜짐 여부 (ProfileService.managerNotifications).
// values 의 키는 ProfileService.MANAGER_NOTIFICATION_KEYS 에서 온다 — rows 와 키가 같아야 화면 토글이 값과 짝지어진다
public record ManagerNotificationsResponse(JsonNode rows, Map<String, Boolean> values) {}
