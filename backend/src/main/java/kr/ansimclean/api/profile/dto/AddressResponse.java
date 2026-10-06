package kr.ansimclean.api.profile.dto;

import java.util.UUID;

/** = 앱 ADDRESSES 항목 { id, label, roadAddress, detailAddress, isDefault }. isDefault = 예약 "기존 주소를 사용할까요?" 의 그 주소 (직접 사용 점검 U15) */
// 주소 한 건의 응답 모양 (예: 주소 목록 GET /customer/addresses 의 항목, 주소 추가 응답). ProfileService 가 Address 엔티티에서 만든다
public record AddressResponse(UUID id, String label, String roadAddress, String detailAddress, boolean isDefault) {}
