package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.PriceQuote;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 가격 계산 (BACKEND_PLAN.md §6.4). 숫자는 전부 catalog_prices·catalog_options·app_settings 에서 온다 — 코드에 상수 없음.
 * 옵션 key 검증은 CatalogService.requireOptions 가 먼저 한다(여기는 검증된 옵션 목록만 받는다).
 * BookingService 의 견적(확인 화면 "요금")과 예약 생성이 같은 계산을 쓴다 — 앱이 본 금액과 저장 금액이 다를 수 없게.
 * 생성 때 나온 값은 예약 행에 고정되어, 가격표·수수료율을 바꿔도 이미 만든 예약·콜·정산은 그대로다.
 */
@Service
@Transactional(readOnly = true)
public class PricingService {
    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    public static final String SETTING_FEE_RATE = "platform_fee_rate"; // app_settings 키: 플랫폼 수수료율 (소수, 예: 0.1)
    public static final String SETTING_DURATION_HOURS = "duration_hours"; // app_settings 키: 카테고리별 기본 소요 시간 객체

    private final CatalogPriceRepository prices;
    private final AppSettingsService settings;

    public PricingService(CatalogPriceRepository prices, AppSettingsService settings) {
        this.prices = prices;
        this.settings = settings;
    }

    /**
     * @param frequency   'recurring' | 'oneTime'
     * @param paidOptions 고객이 고른 옵션 (검증된 엔티티). 이름과 달리 무료가 섞여 와도 되고 유료만 더한다
     */
    // 방문 한 번(회당) 금액을 계산한다: 기본가(가격표) + 유료 옵션 합 = total, 수수료 = total × 수수료율(반올림), 소요 시간.
    // 가격표에 (카테고리, 주기) 행이 없으면 설정 누락으로 보고 INTERNAL(500).
    public PriceQuote quote(String category, String frequency, List<CatalogOption> paidOptions) {
        return quote(category, frequency, paidOptions, null);
    }

    /** 시간 선택 예약은 고객 앱의 시간당 요금과 정기 할인으로 계산한다. */
    public PriceQuote quote(String category, String frequency, List<CatalogOption> paidOptions, Integer hours) {
        if (hours != null && !"repair".equals(category) && (hours < 2 || hours > 8)) throw ApiException.validation("청소 시간은 2~8시간 중에서 골라 주세요");
        long base = prices.findById(new CatalogPrice.Key(category, frequency))
                .orElseThrow(() -> missingSetting("가격표", category + "/" + frequency))
                .getBaseAmount();
        if (hours != null && !"repair".equals(category)) {
            base = Math.multiplyExact((long) settings.integer("cleaning_hourly_amount"), hours);
            if ("recurring".equals(frequency)) base -= settings.integer("cleaning_recurring_discount");
            if (base < 0) throw missingSetting("시간 요금", category);
        }
        // 무료 옵션이 섞여 와도 유료(isFree 가 false)만 더한다. 지금 BookingService 는 무료+유료 목록을 통째로 넘긴다
        long options = paidOptions.stream().filter(o -> !o.isFree()).mapToLong(CatalogOption::getPrice).sum();
        long total = base + options;
        // BigDecimal(십진수 그대로 계산하는 수)로 곱해 double 곱셈의 오차 없이 계산하고, 원 단위로 반올림한다
        // (HALF_UP: .5 는 올림 — 예: 8555.5 → 8556). longValueExact 는 long 범위를 넘으면 잘라 내지 않고 예외를 던진다
        long fee = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(settings.decimal(SETTING_FEE_RATE)))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new PriceQuote(base, options, total, fee, hours != null && !"repair".equals(category) ? hours * 60 : durationMinutes(category));
    }

    // ── 요금표 (사용자 요금표 2026-11-13, V074 app_settings.price_plans — docs/domain.md '요금표 전환') ──

    public static final String SETTING_PRICE_PLANS = "price_plans";

    /**
     * 요금제 하나. key = bookings.price_plan 값, amount = 결제 금액(정기면 한 달 — 원룸 첫 달이면 할인 금액), hours = 1회 소요 시간,
     * visitsPerWeek = 정기면 주 몇 회(1회 요금제면 0), weeks = 정기 기간(주), regularAmount = 할인 전 한 달 금액(할인 안내용)
     */
    public record Plan(String key, long amount, int hours, int visitsPerWeek, int weeks, long regularAmount) {
        public boolean weekly() { return visitsPerWeek > 0; }
        public int visits() { return weekly() ? visitsPerWeek * weeks : 1; }
        public long discount() { return regularAmount - amount; }
    }

    /**
     * 이 예약이 요금표를 쓰면 그 요금제, 아니면 empty(매장·사무실·입주·수리 — 기존 계산).
     * 가정/원룸(home): homeType 필수 — 1회는 가정 첫 1회 체험·원룸 1회, 정기는 주 visitsPerWeek(1~3)회. 분해청소(appliance): 1회만, 후드 크기 필수.
     * studioFirstMonth = 원룸 주 1회 첫 달 할인을 받을 수 있나(판정은 부르는 쪽 — BookingService). 입력이 틀리면 VALIDATION
     */
    public java.util.Optional<Plan> plan(String category, String homeType, String frequency, Integer visitsPerWeek, String hoodSize, boolean studioFirstMonth) {
        var plans = settings.node(SETTING_PRICE_PLANS);
        if (plans == null || plans.isNull()) throw missingSetting("요금표", SETTING_PRICE_PLANS);
        boolean recurring = "recurring".equals(frequency);
        if ("appliance".equals(category)) {
            if (recurring) throw ApiException.validation("분해청소는 1회만 예약할 수 있어요");
            var price = hoodSize == null ? null : plans.path("hood").path("sizes").get(hoodSize);
            if (price == null) throw ApiException.validation("후드 크기를 골라주세요");
            return java.util.Optional.of(new Plan("hood_" + hoodSize, price.asLong(), 0, 0, 0, price.asLong()));
        }
        if (!"home".equals(category)) return java.util.Optional.empty();
        if (!"house".equals(homeType) && !"studio".equals(homeType)) throw ApiException.validation("가정 청소와 원룸 청소 중 골라주세요");
        var type = plans.path(homeType);
        int hours = type.path("hours").asInt();
        if (!recurring) {
            long amount = type.path("house".equals(homeType) ? "trial" : "once").asLong();
            if (amount <= 0 || hours <= 0) throw missingSetting("요금표", homeType);
            return java.util.Optional.of(new Plan("house".equals(homeType) ? "house_trial" : "studio_once", amount, hours, 0, 0, amount));
        }
        if (visitsPerWeek == null || type.path("weekly").get(String.valueOf(visitsPerWeek)) == null)
            throw ApiException.validation("주 1회·2회·3회 중에서 골라주세요. 주 4회 이상은 상담으로 안내해 드려요");
        long regular = type.path("weekly").get(String.valueOf(visitsPerWeek)).asLong();
        var first = type.path("firstMonth").get(String.valueOf(visitsPerWeek));
        long amount = studioFirstMonth && first != null ? first.asLong() : regular;
        int weeks = plans.path("weeks").asInt(4);
        return java.util.Optional.of(new Plan(homeType + "_weekly_" + visitsPerWeek, amount, hours, visitsPerWeek, weeks, regular));
    }

    /**
     * 요금제의 회차별 금액. 정기면 한 달 금액을 회차 수로 나눠 원 단위 내림, 나머지는 첫 회차에. 유료 옵션은 회차마다 더한다.
     * 수수료는 회차 금액마다 기존 수수료율(HALF_UP). 소요 시간은 요금제 값(분해청소는 카테고리 기본)
     */
    public java.util.List<PriceQuote> planVisits(Plan plan, String category, List<CatalogOption> options) {
        int visits = plan.visits();
        long share = plan.amount() / visits;
        long rest = plan.amount() - share * visits;
        long optionTotal = options.stream().filter(o -> !o.isFree()).mapToLong(CatalogOption::getPrice).sum();
        int minutes = plan.hours() > 0 ? plan.hours() * 60 : durationMinutes(category);
        java.util.List<PriceQuote> out = new java.util.ArrayList<>();
        for (int i = 0; i < visits; i++) {
            long base = share + (i == 0 ? rest : 0);
            long total = base + optionTotal;
            out.add(new PriceQuote(base, optionTotal, total, fee(total), minutes));
        }
        return out;
    }

    private long fee(long total) {
        return BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(settings.decimal(SETTING_FEE_RATE)))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 같은 카테고리의 다른 주기 기본가 (정기 할인 안내용). 가격표에 없으면 비어 있다 */
    public java.util.Optional<Long> baseAmount(String category, String frequency) {
        return prices.findById(new CatalogPrice.Key(category, frequency)).map(CatalogPrice::getBaseAmount);
    }

    /** 설정 누락은 운영 문제 — 키 이름은 로그에만, 응답은 §5.0 대로 일반 문구 */
    private static ApiException missingSetting(String what, String key) {
        log.error("{} 이(가) 없어요: {}", what, key);
        return new ApiException(ErrorCode.INTERNAL);
    }

    /**
     * 카테고리 기본 소요 시간(분). app_settings.duration_hours 객체에서 그 카테고리의 시간 수를 읽어 60 을 곱한다.
     * 카테고리 칸이 없으면 설정 누락으로 INTERNAL(500).
     */
    public int durationMinutes(String category) {
        var hours = settings.node(SETTING_DURATION_HOURS).get(category);
        if (hours == null) throw missingSetting("소요 시간 설정", category);
        return hours.asInt() * 60;
    }
}
