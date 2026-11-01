package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.Size;

/** PATCH /customer/addresses/{id} */
// 예약 주소 선택의 '수정' (2026-10-31 사용자 요청): 별칭과 상세주소만 바꾼다. 도로명주소는 바꿀 수 없다 — 잘못 넣었으면 지우고 새로 추가한다.
// label 이 비면 "기타", detailAddress 가 비면 null 로 저장된다 (Address.rename)
public record UpdateAddressRequest(@Size(max = 30) String label,
                                   @Size(max = 200) String detailAddress) {}
