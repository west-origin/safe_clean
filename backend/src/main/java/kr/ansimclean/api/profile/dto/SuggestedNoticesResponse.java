package kr.ansimclean.api.profile.dto;

import java.util.List;

/** GET /customer/profile/suggested-notices — 예약 주의사항 화면이 미리 골라 둘 키 (지금은 반려동물뿐) */
// { notices: ["pet"] } 또는 { notices: [] }. 값은 ProfileService.suggestedNotices 가 가입 때 받은 주거 정보에서 정한다
public record SuggestedNoticesResponse(List<String> notices) {}
