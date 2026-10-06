package kr.ansimclean.api.profile.dto;

import tools.jackson.databind.JsonNode;

import java.util.List;

/** GET /manager/profile — 앱 PROFILE 모양 ("김○○ 매니저", 평점·완료 청소, 메뉴는 app_settings.manager_profile_menu) */
// ProfileService.managerProfile 이 만든다. menu 는 DB 설정값(JSON)을 그대로 싣는다
public record ManagerProfileResponse(String name, List<Stat> stats, JsonNode menu) {
    /** 통계 한 칸 — 예: { label: "평점", value: "-" }, { label: "완료 청소", value: "12건" } */
    public record Stat(String label, String value) {}
}
