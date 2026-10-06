package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /customer/addresses */
// 설정의 주소 추가 요청. 예약에서 새로 쓴 주소를 남길 때(ProfileService.rememberAddress)도 label 을 null 로 해 이 모양을 쓴다.
// label 이 비면 "기타" 로 저장된다 (Address.create)
public record NewAddressRequest(@Size(max = 30) String label,
                                @NotBlank(message = "도로명주소를 입력해주세요") @Size(max = 200) String roadAddress,
                                @Size(max = 200) String detailAddress) {}
