package kr.ansimclean.api.profile.dto;

import java.util.List;

/** GET /manager/profile/{key} — 읽기 전용 정보 화면 (앱 PROFILE_DETAILS). rows 또는 chips 중 하나 */
// ProfileService.managerProfileDetail 이 key(info·area·identity·education·account)마다 만든다.
// title = 화면 제목, chips = 활동 지역 목록(area 만), note = 화면 아래 안내 문구(없으면 null)
public record ProfileDetailResponse(String title, List<Row> rows, List<String> chips, String note) {
    /** 정보 한 줄 — 예: { label: "은행", value: "미등록" } */
    public record Row(String label, String value) {}
}
