package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /customer/profile/address (가입 Auth/ProfileAddress) */
// 가입 주소 단계의 요청 본문. ProfileService.saveDefaultAddress 가 받는다 (기본 주소가 이미 있으면 새로 만들지 않고 고친다).
// @NotBlank·@Size: 컨트롤러의 @Valid 가 검사한다. 어기면 400 VALIDATION — message 에 적은 한국어 문구가 응답에 나간다 (GlobalExceptionHandler)
public record AddressRequest(@NotBlank(message = "도로명주소를 입력해주세요") @Size(max = 200) String roadAddress,
                             @Size(max = 200) String detailAddress) {}
