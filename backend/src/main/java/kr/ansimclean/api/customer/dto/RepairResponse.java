package kr.ansimclean.api.customer.dto;

import tools.jackson.databind.JsonNode;

// 수리 탭(Repair 화면) 응답. hero 는 DB 에 저장된 JSON(문구·이미지 이름)을 JsonNode 로 모양 그대로 싣는다
/** GET /customer/repair — { hero } (app_settings.repair_hero) */
public record RepairResponse(JsonNode hero) {}
