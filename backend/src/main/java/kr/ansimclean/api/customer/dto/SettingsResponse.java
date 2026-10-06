package kr.ansimclean.api.customer.dto;

import kr.ansimclean.api.profile.dto.SettingsProfile;
import tools.jackson.databind.JsonNode;

import java.util.Map;

// 설정 탭(Settings 화면) 응답. header·groups 는 app_settings 의 settings_header·settings_groups JSON 그대로,
// profile 은 ProfileService.settingsProfile 이 만든 값, notifications 는 { push: true, marketing: false } 같은 토글 값
/** GET /customer/settings — 앱 getSettings() 모양 */
public record SettingsResponse(JsonNode header, SettingsProfile profile, JsonNode groups, Map<String, Boolean> notifications) {}
