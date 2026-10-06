package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.AddressSearchResponse;
import kr.ansimclean.api.catalog.dto.CategoryResponse;
import kr.ansimclean.api.catalog.dto.ConfirmCopyResponse;
import kr.ansimclean.api.catalog.dto.NoticeChipsResponse;
import kr.ansimclean.api.catalog.dto.OptionsResponse;
import kr.ansimclean.api.catalog.dto.TimeSlotsResponse;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** GET /api/v1/catalog/** — 인증 없음. 앱 api/customer.js·manager.js 의 카탈로그 함수와 1:1 (docs/api.md §5.2·5.3) */
// 고객 앱 예약 화면(예: Booking/Options)과 매니저 앱 거절·중단 사유 시트가 쓰는 참조 데이터를 준다. 로그인 없이 열리는 것은
// SecurityConfig 의 permitAll 목록에 /catalog/** 가 있기 때문이다. 입출력만 하고 규칙은 CatalogService 에 있다.
@RestController
@RequestMapping(SecurityConfig.API + "/catalog")
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    /**
     * GET /catalog/categories/{category} → 카테고리 라벨·프로모션 배너 종류. 고객 앱 getBookingCategory 가 부른다.
     * 모르는 카테고리는 404 (앱은 404 를 null 로 바꾼다). 아래 …/options 등 카테고리 경로도 모르는 값이면 404 다.
     */
    @GetMapping("/categories/{category}")
    public CategoryResponse category(@PathVariable String category) {
        return service.category(category);
    }

    /** GET …/options → 무료·유료 옵션 목록과 무료 선택 한도. 고객 앱 getBookingOptions (예: Booking/Options 화면) */
    @GetMapping("/categories/{category}/options")
    public OptionsResponse options(@PathVariable String category) {
        return service.options(category);
    }

    /** GET …/notice-chips → 주의사항 칩과 화면 문구. 고객 앱 getNoticeChips (예: Booking/NotesRequests 화면) */
    @GetMapping("/categories/{category}/notice-chips")
    public NoticeChipsResponse noticeChips(@PathVariable String category) {
        return service.noticeChips(category);
    }

    /** GET …/confirm-copy → 확인 화면 문구와 주기별 부가 설명. 고객 앱 getConfirmCopy (예: Booking/Confirm 화면) */
    @GetMapping("/categories/{category}/confirm-copy")
    public ConfirmCopyResponse confirmCopy(@PathVariable String category) {
        return service.confirmCopy(category);
    }

    /**
     * GET /catalog/time-slots → 오전·오후 시간대, 예약 가능 기간, 변경 기한. 고객 앱 getTimeSlots (예: Booking/DateTime 화면).
     * category·date 는 선택 값이다(지금 고객 앱은 둘 다 보내지 않는다). 보내도 응답 내용은 같고 검사만 한다:
     * 모르는 category 는 404, date 가 "YYYY-MM-DD" 형식이 아니면 변환 실패를 GlobalExceptionHandler 가 400(VALIDATION) 으로.
     */
    @GetMapping("/time-slots")
    public TimeSlotsResponse timeSlots(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.timeSlots(category, date);
    }

    /**
     * GET /catalog/reasons?kind=reject|pause → 사유 문구 목록. 매니저 앱 getRejectReasons·getPauseReasons 가 부른다.
     * kind 는 필수 값이라 빠지면 400. 다른 값이면 CatalogService.reasons 가 VALIDATION 을 던진다.
     */
    @GetMapping("/reasons")
    public List<String> reasons(@RequestParam String kind) {
        return service.reasons(kind);
    }

    /** GET /catalog/address?q=… → 주소 검색 결과. 고객 앱 searchAddress (검색어가 두 글자 이상일 때만 부른다) */
    @GetMapping("/address")
    public AddressSearchResponse address(@RequestParam(required = false) String q) {
        return service.searchAddress(q);
    }
}
