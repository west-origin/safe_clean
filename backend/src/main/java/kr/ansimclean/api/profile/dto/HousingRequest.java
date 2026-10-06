package kr.ansimclean.api.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PUT /customer/profile/housing (가입 Auth/ProfileHousing). 값은 앱 HOUSING_TYPES·PET_OPTIONS 의 id */
// 요청이자 응답: ProfileService.saveHousing 이 저장한 뒤 이 값을 그대로 돌려준다.
// @Pattern: 정해진 값(아래 목록) 중 하나가 아니면 400 VALIDATION. 평수·방·화장실은 글자(최대 20자)로 받는다
public record HousingRequest(
        @NotBlank @Pattern(regexp = "apartment|studio|house|other", message = "거주형태를 선택해주세요") String housingType,
        @Size(max = 20) String size,
        boolean sizeUnknown, // '평수 모름' 을 골랐는가 — true 면 보낸 평수는 저장하지 않는다 (CustomerHousing.update)
        @Size(max = 20) String rooms,
        @Size(max = 20) String bathrooms,
        @NotBlank @Pattern(regexp = "yes|no", message = "반려동물 여부를 선택해주세요") String pet) {}
