package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.AddressSearchResponse;
import kr.ansimclean.api.catalog.dto.CategoryResponse;
import kr.ansimclean.api.catalog.dto.ConfirmCopyResponse;
import kr.ansimclean.api.catalog.dto.NoticeChipsResponse;
import kr.ansimclean.api.catalog.dto.OptionsResponse;
import kr.ansimclean.api.catalog.dto.TimeSlotsResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.format.KoreanFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 카탈로그(참조 데이터) 읽기. 응답 모양은 앱 mock.js 와 같다 (docs/api.md). */
// 두 가지 일을 한다. ① CatalogController 의 GET /catalog/** 응답 만들기 (고객 앱 예약 플로우, 매니저 앱 사유 시트)
// ② 다른 기능이 부르는 검사·변환 (예: BookingService 의 옵션·시간대 검사, JobService 의 사유 검사와 key → 라벨 변환).
// 숫자·문구는 카탈로그 테이블과 app_settings(AppSettingsService)에서 읽는다. DB 에 쓰지 않는다.
@Service
@Transactional(readOnly = true)
public class CatalogService {
    // app_settings 의 키 이름들 (값은 DB 에 있고 코드에는 이름만 둔다)
    public static final String SETTING_FREE_OPTION_LIMIT = "free_option_limit";
    public static final String SETTING_TIME_SLOTS = "time_slots";
    public static final String SETTING_NOTES_COPY = "notes_copy";
    public static final String SETTING_CONFIRM_COPY = "confirm_copy";
    public static final String SETTING_CANCEL_DEADLINE_HOURS = "cancel_deadline_hours";
    /** 정기청소 개월 수 = 회차 수 (매월 1회, V021 에서 6). 회차를 만드는 BookingService 도 이 키를 쓴다 (BookingService.SETTING_SERIES_TERM_MONTHS) */
    public static final String SETTING_SERIES_TERM_MONTHS = "series_term_months";

    private final ServiceCategoryRepository categories;
    private final CatalogOptionRepository options;
    private final CatalogNoticeChipRepository chips;
    private final CatalogFrequencyCopyRepository frequencyCopy;
    private final CatalogReasonRepository reasons;
    private final ChecklistTemplateRepository checklists;
    private final AppSettingsService settings;

    public CatalogService(ServiceCategoryRepository categories, CatalogOptionRepository options, CatalogNoticeChipRepository chips,
                          CatalogFrequencyCopyRepository frequencyCopy, CatalogReasonRepository reasons, ChecklistTemplateRepository checklists,
                          AppSettingsService settings) {
        this.categories = categories;
        this.options = options;
        this.chips = chips;
        this.frequencyCopy = frequencyCopy;
        this.reasons = reasons;
        this.checklists = checklists;
        this.settings = settings;
    }

    /**
     * 카테고리 한 행을 찾는다. 없으면 ApiException NOT_FOUND(404). 다른 기능도 카테고리 검사와
     * 라벨("가정/원룸 청소" 등)을 얻을 때 부른다 (예: BookingService 예약 생성, JobService 작업 화면).
     */
    public ServiceCategory requireCategory(String key) {
        return categories.findById(key).orElseThrow(ApiException::notFound);
    }

    /** GET /catalog/categories/{category} 응답: 라벨·프로모션 종류·배너 문구 */
    public CategoryResponse category(String key) {
        ServiceCategory c = requireCategory(key);
        return new CategoryResponse(c.getLabel(), c.getPromo(), c.getPromoText());
    }

    /**
     * GET …/options 응답: 옵션을 무료·유료 두 목록으로 나눈다. 유료만 가격 글자("+3,000원")가 있고
     * 무료는 price 를 null 로 두어 JSON 에서 빠진다. freeLimit 은 app_settings.free_option_limit.
     */
    public OptionsResponse options(String key) {
        requireCategory(key); // 모르는 카테고리는 빈 목록 대신 404
        List<OptionsResponse.Option> free = new ArrayList<>();
        List<OptionsResponse.Option> paid = new ArrayList<>();
        for (CatalogOption o : options.findByCategoryKeyOrderBySort(key)) {
            if (o.isFree()) free.add(new OptionsResponse.Option(o.getKey(), o.getLabel(), null, o.getIcon()));
            else paid.add(new OptionsResponse.Option(o.getKey(), o.getLabel(), KoreanFormat.wonSigned(o.getPrice()), o.getIcon()));
        }
        return new OptionsResponse(free, paid, settings.integer(SETTING_FREE_OPTION_LIMIT));
    }

    /**
     * GET …/notice-chips 응답: 칩 목록 + 화면 문구(app_settings.notes_copy 를 JSON 그대로).
     * 칩은 카테고리별 행이지만 notes_copy 는 한 벌이라 어느 카테고리든 같은 문구가 나간다.
     */
    public NoticeChipsResponse noticeChips(String key) {
        requireCategory(key);
        List<NoticeChipsResponse.Chip> list = chips.findByCategoryKeyOrderBySort(key).stream()
                .map(c -> new NoticeChipsResponse.Chip(c.getKey(), c.getLabel(), c.getIcon())).toList();
        return new NoticeChipsResponse(list, settings.node(SETTING_NOTES_COPY));
    }

    /**
     * GET …/confirm-copy 응답: 확인 화면 문구(app_settings.confirm_copy) + 주기별 부가 설명 + 정기청소 회차 수(seriesTermMonths).
     * 설명 행이 없는 주기는 맵에서 빠진다 (수리 카테고리는 oneTime 행만 있다).
     * 회차 수는 주기 화면(Booking/Frequency)의 '6개월 동안 6회' 안내가 쓴다 — 앱 고정 글자였을 때는 운영자가 회차 수를 바꿔도
     * 안내가 그대로였다 (문제 목록 25번). 정기가 없는 카테고리(수리)에도 같은 값이 실린다(쓰지 않는다)
     */
    public ConfirmCopyResponse confirmCopy(String key) {
        requireCategory(key);
        Map<String, String> descriptions = new LinkedHashMap<>(); // LinkedHashMap → 넣은 순서를 지키는 맵 (JSON 키 순서도 그대로)
        // 순서는 앱 FREQUENCY_OPTIONS 와 같이 recurring → oneTime
        List<CatalogFrequencyCopy> rows = frequencyCopy.findByCategoryKey(key);
        for (String f : List.of("recurring", "oneTime")) {
            rows.stream().filter(r -> r.getFrequency().equals(f)).findFirst().ifPresent(r -> descriptions.put(f, r.getDescription()));
        }
        return new ConfirmCopyResponse(settings.node(SETTING_CONFIRM_COPY), descriptions, seriesTermMonths());
    }

    /** 정기청소 개월 수 = 회차 수 (app_settings.series_term_months) */
    public int seriesTermMonths() {
        return settings.integer(SETTING_SERIES_TERM_MONTHS);
    }

    /** 날짜별 마감 시각은 결정 §13-6(b): 항상 열려 있다 → unavailable 은 빈 배열. date 는 형식만 검사한다 */
    // GET /catalog/time-slots 응답. am·pm 은 app_settings.time_slots 의 두 배열, 기간·기한은 bookableDays·cancelDeadlineHours 값.
    // date 는 이 메서드에서 쓰지 않는다 (형식 검사는 컨트롤러의 날짜 변환에서 끝난다).
    public TimeSlotsResponse timeSlots(String category, LocalDate date) {
        if (category != null) requireCategory(category); // 보냈을 때만 검사 — 모르는 카테고리면 404
        JsonNode slots = settings.node(SETTING_TIME_SLOTS);
        return new TimeSlotsResponse(strings(slots.get("am")), strings(slots.get("pm")), List.of(), bookableDays(), cancelDeadlineHours());
    }

    public static final String SETTING_BOOKING_HORIZON_DAYS = "booking_horizon_days";

    /** 오늘부터 며칠 뒤까지 예약할 수 있나 (V026, 기본 60). 예약 생성·변경과 앱 달력이 같은 값을 쓴다 (전체 재점검 B1) */
    public int bookableDays() {
        return settings.integer(SETTING_BOOKING_HORIZON_DAYS);
    }

    /**
     * 예약 생성·견적의 옵션 검증 (§6.1): 모르는 key 는 VALIDATION, 무료 옵션은 free_option_limit 까지.
     * @return 고른 옵션 엔티티 (가격 계산에 쓴다)
     */
    // BookingService 의 견적(quote)과 예약 생성이 같은 검증을 쓴다. 돌려준 목록(무료+유료)을 그대로 PricingService.quote 에 넘긴다.
    // 무료 칸에 유료 key(또는 반대)를 넣어도 "알 수 없는 옵션" 으로 거절한다 — 그래야 무료 한도 검사와
    // 예약에 저장되는 무료·유료 key 목록이 실제 옵션 종류와 어긋나지 않는다.
    public List<CatalogOption> requireOptions(String category, List<String> freeKeys, List<String> paidKeys) {
        int limit = settings.integer(SETTING_FREE_OPTION_LIMIT);
        Set<String> free = new LinkedHashSet<>(freeKeys);   // 같은 key 두 번 → 한 번 (한도 검사도 중복 제거 뒤에)
        Set<String> paid = new LinkedHashSet<>(paidKeys);
        if (free.size() > limit) throw ApiException.validation("무료 옵션은 " + limit + "개까지 고를 수 있어요");
        Map<String, CatalogOption> byKey = new LinkedHashMap<>(); // 이 카테고리의 옵션을 key 로 찾기 쉽게 모아 둔다
        for (CatalogOption o : options.findByCategoryKeyOrderBySort(category)) byKey.put(o.getKey(), o);
        List<CatalogOption> chosen = new ArrayList<>(); // 무료 먼저, 그다음 유료 순서
        for (String key : free) chosen.add(requireOption(byKey, key, true));
        for (String key : paid) chosen.add(requireOption(byKey, key, false));
        return chosen;
    }

    /** key 가 이 카테고리 옵션에 있고 종류(무료/유료)도 기대와 같으면 그 옵션, 아니면 VALIDATION(400) */
    private static CatalogOption requireOption(Map<String, CatalogOption> byKey, String key, boolean free) {
        CatalogOption o = byKey.get(key);
        if (o == null || o.isFree() != free) throw ApiException.validation("알 수 없는 옵션이에요: " + key);
        return o;
    }

    /** 주의사항 칩 key 검증 (§6.1) */
    // 예약 생성(BookingService) 때 부른다. 목록에 없는 key 가 하나라도 있으면 VALIDATION(400) 을 던진다
    public void requireNoticeKeys(String category, List<String> keys) {
        List<String> known = chips.findByCategoryKeyOrderBySort(category).stream().map(CatalogNoticeChip::getKey).toList();
        for (String key : keys) {
            if (!known.contains(key)) throw ApiException.validation("알 수 없는 주의사항이에요: " + key);
        }
    }

    /** "HH:mm" 이 app_settings.time_slots(am+pm) 안에 있는가 (§6.1 예약 조건) */
    // BookingService 가 예약 날짜·시간 검사(requireBookableSchedule)에서 부른다. false 면 그쪽이 SLOT_UNAVAILABLE 로 거절한다
    public boolean isTimeSlot(String hhmm) {
        JsonNode slots = settings.node(SETTING_TIME_SLOTS);
        return strings(slots.get("am")).contains(hhmm) || strings(slots.get("pm")).contains(hhmm);
    }

    /** 취소·변경 기한 (§6.1: starts_at − now ≥ 이 시간) */
    // 고객 예약의 취소·변경 기한 판단은 이 값을 쓴다 (예: BookingService 의 취소·일정 변경, OfferService 의 매칭 실패 알림).
    // 매니저 방문 취소의 벌점 기준 시간은 이것과 별개로 app_settings.manager_penalty 에 있다.
    // 앱에도 같은 값이 내려간다 — time-slots·내 예약·고객 홈 응답의 changeDeadlineHours. 날짜 화면의 '예약한 뒤에는 바꾸거나 취소할 수 없어요',
    // 잠긴 카드·정기 회차 취소 시트 안내, 거절 문구(Booking.cancel·reschedule)가 서버의 실제 거절과 같은 시간을 말하게 한다 (문제 목록 25번)
    public int cancelDeadlineHours() {
        return settings.integer(SETTING_CANCEL_DEADLINE_HOURS);
    }

    /** GET /catalog/reasons 응답: 사유 문구만 sort 순서로. kind 가 reject·pause 가 아니면 VALIDATION(400) */
    public List<String> reasons(String kind) {
        if (!List.of("reject", "pause").contains(kind)) throw ApiException.validation("kind 는 reject 또는 pause 여야 해요");
        return reasons.findByKindOrderBySort(kind).stream().map(CatalogReason::getLabel).toList();
    }

    /** 거절·중단 사유가 카탈로그에 있는가 (§6.1 pause, §6.2 reject) */
    // 콜 거절(OfferService, "reject")과 작업 일시정지(JobService, "pause")가 요청 사유를 검사할 때 부른다
    public boolean isReason(String kind, String label) {
        return reasons.findByKindOrderBySort(kind).stream().anyMatch(r -> r.getLabel().equals(label));
    }

    /** 옵션 key → 라벨 (작업 화면 scope). 모르는 key 는 그대로 둔다(과거 예약이 삭제된 옵션을 가리킬 수 있다) */
    public List<String> optionLabels(String category, List<String> keys) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (CatalogOption o : options.findByCategoryKeyOrderBySort(category)) byKey.put(o.getKey(), o.getLabel());
        return keys.stream().map(k -> byKey.getOrDefault(k, k)).toList(); // getOrDefault(k, k): 라벨이 없으면 key 글자 그대로
    }

    /** 주의사항 key → 라벨 (콜 카드 note, 작업 화면 requests) */
    // 모르는 key 는 optionLabels 와 같이 key 글자 그대로 둔다
    public List<String> noticeLabels(String category, List<String> keys) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (CatalogNoticeChip c : chips.findByCategoryKeyOrderBySort(category)) byKey.put(c.getKey(), c.getLabel());
        return keys.stream().map(k -> byKey.getOrDefault(k, k)).toList();
    }

    /** 체크리스트 템플릿. 그 카테고리에 없으면 home 템플릿(B1 판단: 처음엔 home 만 seed. 지금은 V028·V032 로 모든 카테고리에 있다) */
    // JobService.createChecklist 가 콜 수락(배정) 때 이 목록을 작업 체크리스트 행(job_checklist_items)으로 복사한다.
    // home 대체는 새 카테고리를 넣고 템플릿을 빠뜨렸을 때만 일어난다 — 그러면 가정 청소 항목이 붙으니 템플릿도 같이 넣는다
    public List<ChecklistTemplate> checklistTemplate(String category) {
        List<ChecklistTemplate> rows = checklists.findByCategoryKeyOrderBySort(category);
        return rows.isEmpty() ? checklists.findByCategoryKeyOrderBySort("home") : rows;
    }

    /** "기본청소"·"입주청소" 같은 짧은 라벨 (정산 목록 제목). 없으면 전체 라벨 */
    // 짧은 라벨은 app_settings.category_short_labels 객체({ "home": "기본청소", … })에서 찾는다
    public String shortLabel(String category) {
        JsonNode node = settings.node("category_short_labels").get(category); // 그 카테고리 칸이 없으면 null
        return node == null ? requireCategory(category).getLabel() : node.asString();
    }

    /** 주소 검색 API 연동 전(§13-4): 빈 결과. 연동 시 여기만 바뀐다 */
    // 결과가 비어도 고객 앱 주소 검색 화면(BookingAddressSearch)은 "다음" 버튼으로 입력한 검색어를 그대로 주소로 넘긴다
    public AddressSearchResponse searchAddress(String query) {
        String q = query == null ? "" : query.trim();
        return new AddressSearchResponse(q, List.of());
    }

    /** JSON 배열 → 문자열 목록. 배열이 없으면(null) 빈 목록 — time_slots 의 am·pm 을 읽을 때 쓴다 */
    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) array.forEach(n -> out.add(n.asString()));
        return out;
    }
}
