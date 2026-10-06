package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.PriceQuote;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 가격 규칙 (BACKEND_PLAN.md §6.4): base + 유료 옵션, 수수료 반올림, 소요 시간 */
// PricingService.quote 단위 테스트. 가격표 레포지토리·설정(AppSettingsService)은 Mockito 목이고,
// 수수료율 0.1, 가정청소 소요 3시간을 목의 답으로 정해 계산 결과만 본다 (DB 없이).
class PricingServiceTest {
    CatalogPriceRepository prices = mock(CatalogPriceRepository.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    PricingService service = new PricingService(prices, settings);

    /** 기본가만 답하는 가짜 가격표 행 */
    static CatalogPrice price(long amount) {
        CatalogPrice p = mock(CatalogPrice.class);
        when(p.getBaseAmount()).thenReturn(amount);
        return p;
    }

    /** 무료 여부·가격만 답하는 가짜 옵션 */
    static CatalogOption option(boolean free, long price) {
        CatalogOption o = mock(CatalogOption.class);
        when(o.isFree()).thenReturn(free);
        when(o.getPrice()).thenReturn(price);
        return o;
    }

    @Test
    void 금액은_기본_더하기_유료_옵션이고_무료_옵션은_0원() {
        CatalogPrice base = price(80000);
        List<CatalogOption> chosen = List.of(option(true, 0), option(false, 3000), option(false, 2500));
        when(prices.findById(any())).thenReturn(Optional.of(base));
        when(settings.decimal(PricingService.SETTING_FEE_RATE)).thenReturn(0.1);
        when(settings.node(PricingService.SETTING_DURATION_HOURS)).thenReturn(JsonMapper.builder().build().readTree("{\"home\":3}"));

        PriceQuote q = service.quote("home", "recurring", chosen);

        // 80,000 + 유료 3,000 + 2,500 = 85,500. 수수료 10% = 8,550, 매니저 몫 = 85,500 − 8,550. 3시간 = 180분
        assertThat(q.base()).isEqualTo(80000);
        assertThat(q.options()).isEqualTo(5500);
        assertThat(q.total()).isEqualTo(85500);
        assertThat(q.platformFee()).isEqualTo(8550);
        assertThat(q.managerPay()).isEqualTo(76950);
        assertThat(q.durationMinutes()).isEqualTo(180);
    }

    @Test
    void 수수료는_반올림() {
        CatalogPrice base = price(85555);
        when(prices.findById(any())).thenReturn(Optional.of(base));
        when(settings.decimal(PricingService.SETTING_FEE_RATE)).thenReturn(0.1);
        when(settings.node(PricingService.SETTING_DURATION_HOURS)).thenReturn(JsonMapper.builder().build().readTree("{\"home\":3}"));
        assertThat(service.quote("home", "oneTime", List.of()).platformFee()).isEqualTo(8556); // 8555.5 → 8556
    }
}
