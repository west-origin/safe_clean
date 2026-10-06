package kr.ansimclean.api.profile.dto;

/** GET /customer/addresses/default — 앱 SAVED_ADDRESS 모양 */
// 예약 주소 단계의 "기존 주소를 사용할까요?" 가 쓰는 기본 주소. 없으면 서버는 404, 앱 어댑터가 null 로 바꾼다
public record DefaultAddressResponse(String roadAddress, String detailAddress) {}
