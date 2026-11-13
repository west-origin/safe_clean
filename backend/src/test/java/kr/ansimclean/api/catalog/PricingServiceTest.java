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
    void 선택한_시간과_옵션으로_앞_화면과_같은_금액을_계산한다() {
        CatalogPrice original = price(80000);
        when(prices.findById(any())).thenReturn(Optional.of(original));
        when(settings.integer("cleaning_hourly_amount")).thenReturn(20800);
        when(settings.integer("cleaning_recurring_discount")).thenReturn(2000);
        when(settings.decimal(PricingService.SETTING_FEE_RATE)).thenReturn(0.1);
        PriceQuote q = service.quote("home", "recurring", List.of(option(false, 3000)), 2);
        assertThat(q.base()).isEqualTo(39600);
        assertThat(q.total()).isEqualTo(42600);
        assertThat(q.platformFee()).isEqualTo(4260);
        assertThat(q.durationMinutes()).isEqualTo(120);
        PriceQuote oneTime = service.quote("store", "oneTime", List.of(), 4);
        assertThat(oneTime.total()).isEqualTo(83200);
        assertThat(oneTime.durationMinutes()).isEqualTo(240);
    }

    @Test
    void 시간_선택은_유효한_범위만_요금에_반영한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.quote("home", "oneTime", List.of(), 1)).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.quote("home", "oneTime", List.of(), 9)).isInstanceOf(kr.ansimclean.api.common.error.ApiException.class);
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

    // ── 요금표 요금제 (요금표 전환 2026-11-13, V074 price_plans) ──

    static final String PLANS = """
            {"house":{"hours":3,"trial":50000,"weekly":{"1":329000,"2":599000,"3":889000}},
             "studio":{"hours":2,"once":69000,"weekly":{"1":229000,"2":399000,"3":599000},"firstMonth":{"1":199000}},
             "hood":{"sizes":{"600":130000,"900":150000,"1200":200000}},"weeks":4}""";

    void plans() {
        when(settings.node(PricingService.SETTING_PRICE_PLANS)).thenReturn(JsonMapper.builder().build().readTree(PLANS));
        when(settings.decimal(PricingService.SETTING_FEE_RATE)).thenReturn(0.1);
        when(settings.node(PricingService.SETTING_DURATION_HOURS)).thenReturn(JsonMapper.builder().build().readTree("{\"appliance\":2}"));
    }

    @Test
    void 가정_첫_체험과_원룸_1회와_후드_크기는_요금표_금액이다() {
        plans();
        var trial = service.plan("home", "house", "oneTime", null, null, false).orElseThrow();
        assertThat(trial.key()).isEqualTo("house_trial");
        assertThat(trial.amount()).isEqualTo(50000);
        assertThat(trial.hours()).isEqualTo(3);
        var once = service.plan("home", "studio", "oneTime", null, null, false).orElseThrow();
        assertThat(once.key()).isEqualTo("studio_once");
        assertThat(once.amount()).isEqualTo(69000);
        assertThat(service.planVisits(once, "home", List.of()).get(0).durationMinutes()).isEqualTo(120);
        var hood = service.plan("appliance", null, "oneTime", null, "900", false).orElseThrow();
        assertThat(hood.key()).isEqualTo("hood_900");
        // 추가 요금(유료 옵션)은 더한다
        var visit = service.planVisits(hood, "appliance", List.of(option(false, 20000), option(true, 0))).get(0);
        assertThat(visit.total()).isEqualTo(170000);
        assertThat(visit.platformFee()).isEqualTo(17000);
    }

    @Test
    void 요금표를_쓰지_않는_카테고리는_비어_있다() {
        plans();
        assertThat(service.plan("store", null, "oneTime", null, null, false)).isEmpty();
        assertThat(service.plan("repair", null, "oneTime", null, null, false)).isEmpty();
    }

    @Test
    void 주_N회_정기는_한_달_금액을_회차로_나누고_나머지는_첫_회차() {
        plans();
        var plan = service.plan("home", "house", "recurring", 3, null, false).orElseThrow();
        assertThat(plan.key()).isEqualTo("house_weekly_3");
        assertThat(plan.visits()).isEqualTo(12);
        List<PriceQuote> visits = service.planVisits(plan, "home", List.of());
        assertThat(visits).hasSize(12);
        assertThat(visits.get(0).base()).isEqualTo(74083 + 4); // 889,000 = 74,083 × 12 + 4
        assertThat(visits.get(1).base()).isEqualTo(74083);
        assertThat(visits.stream().mapToLong(PriceQuote::total).sum()).isEqualTo(889000);
        assertThat(visits.get(0).durationMinutes()).isEqualTo(180);
    }

    @Test
    void 원룸_주_1회_첫_달은_할인_금액() {
        plans();
        var first = service.plan("home", "studio", "recurring", 1, null, true).orElseThrow();
        assertThat(first.amount()).isEqualTo(199000);
        assertThat(first.discount()).isEqualTo(30000);
        assertThat(service.plan("home", "studio", "recurring", 1, null, false).orElseThrow().amount()).isEqualTo(229000);
        assertThat(service.plan("home", "studio", "recurring", 2, null, true).orElseThrow().amount()).isEqualTo(399000); // 주 2회는 첫 달 할인 없음
        assertThat(service.planVisits(first, "home", List.of()).stream().mapToLong(PriceQuote::total).sum()).isEqualTo(199000);
    }

    @Test
    void 요금제_입력이_틀리면_VALIDATION() {
        plans();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.plan("home", null, "oneTime", null, null, false))
                .hasMessageContaining("가정 청소와 원룸 청소");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.plan("home", "house", "recurring", 4, null, false))
                .hasMessageContaining("주 4회 이상은 상담");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.plan("appliance", null, "recurring", null, "900", false))
                .hasMessageContaining("1회만");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.plan("appliance", null, "oneTime", null, "750", false))
                .hasMessageContaining("후드 크기");
    }
}
