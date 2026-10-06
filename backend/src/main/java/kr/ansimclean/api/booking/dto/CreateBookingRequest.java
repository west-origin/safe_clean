package kr.ansimclean.api.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// 예약 생성 요청 본문. 고객 앱 api/customer.js 의 createBooking(청소)·createRepairBooking(수리)이 보내고,
// BookingController.create 가 받아 BookingService 로 넘긴다. 금액은 여기 없다 — 서버가 견적을 다시 계산해 저장한다.
// 검사 표시: @NotBlank = 빈 값 금지, @Pattern = 정규식과 일치해야 함, @Size = 길이·개수 상한, @Valid = 안쪽 Repair 도 검사.
// 어기면 400 VALIDATION 이고, message 가 한국어면 그 문구가 응답 message 로 그대로 나간다 (GlobalExceptionHandler).
// 칸: category = 카테고리 키(수리는 "repair"), freeOptions·paidOptions = 고른 옵션 키, notices = 현장 주의사항 키,
// request = 요청 사항 자유 입력, repairType = 수리 종류 키(수리 예약만), repair = 수리 상세, saveAddress = 새 주소를 내 주소 목록에 남길지.
/** POST /customer/bookings (+ Idempotency-Key). 앱 초안(bookingDraft) 그대로 */
public record CreateBookingRequest(@NotBlank String category,
                                   @Pattern(regexp = "recurring|oneTime", message = "frequency 는 recurring 또는 oneTime 이에요") String frequency,
                                   @NotBlank(message = "방문 일정을 선택해주세요") @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "date 는 YYYY-MM-DD 형식이에요") String date,
                                   @NotBlank(message = "방문 일정을 선택해주세요") @Pattern(regexp = "\\d{2}:\\d{2}", message = "time 은 HH:mm 형식이에요") String time,
                                   @NotBlank(message = "방문할 주소를 입력해주세요") @Size(max = 200) String roadAddress,
                                   @Size(max = 200) String detailAddress,
                                   List<String> freeOptions,
                                   List<String> paidOptions,
                                   List<String> notices,
                                   @Size(max = 1000) String request,
                                   @Size(max = 30) String repairType,
                                   @Valid Repair repair,
                                   Boolean saveAddress) {
    /** 수리 예약(category=repair) 의 가전·증상·상세·사진. 가전·증상은 앱 카탈로그의 표시 문구 그대로 + 키(applianceKey/symptomKey, 통계·매칭용, 선택) */
    public record Repair(@Size(max = 40) String appliance, @Size(max = 60) String symptom, @Size(max = 40) String applianceKey, @Size(max = 40) String symptomKey, @Size(max = 1000) String detail,
                         @Size(max = 5, message = "사진은 5장까지 첨부할 수 있어요") List<@Size(max = 300) String> photos) {
        // photos = POST /customer/repair-photos 가 돌려준 path 들. 이 고객 것인지는 BookingService.ownPhotos 가 확인한다
        // 압축 생성자: 사진 목록이 빠지면(null) 빈 목록으로 채운다
        public Repair {
            if (photos == null) photos = List.of();
        }
    }
    // 압축 생성자: 빠진 칸을 기본값으로 채운다 — frequency 없으면 정기(recurring), 목록은 빈 목록, saveAddress 는 false
    public CreateBookingRequest {
        if (frequency == null) frequency = "recurring";
        if (freeOptions == null) freeOptions = List.of();
        if (paidOptions == null) paidOptions = List.of();
        if (notices == null) notices = List.of();
        if (saveAddress == null) saveAddress = false;
    }
}
