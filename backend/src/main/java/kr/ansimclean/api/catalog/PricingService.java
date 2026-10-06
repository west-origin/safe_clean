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
        long base = prices.findById(new CatalogPrice.Key(category, frequency))
                .orElseThrow(() -> missingSetting("가격표", category + "/" + frequency))
                .getBaseAmount();
        // 무료 옵션이 섞여 와도 유료(isFree 가 false)만 더한다. 지금 BookingService 는 무료+유료 목록을 통째로 넘긴다
        long options = paidOptions.stream().filter(o -> !o.isFree()).mapToLong(CatalogOption::getPrice).sum();
        long total = base + options;
        // BigDecimal(십진수 그대로 계산하는 수)로 곱해 double 곱셈의 오차 없이 계산하고, 원 단위로 반올림한다
        // (HALF_UP: .5 는 올림 — 예: 8555.5 → 8556). longValueExact 는 long 범위를 넘으면 잘라 내지 않고 예외를 던진다
        long fee = BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(settings.decimal(SETTING_FEE_RATE)))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new PriceQuote(base, options, total, fee, durationMinutes(category));
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
