package kr.ansimclean.api.catalog.dto;

/**
 * 1회 방문의 금액 (BACKEND_PLAN.md §6.4). 예약 생성 시 bookings 행에 그대로 고정된다.
 * @param base            catalog_prices[category][frequency]
 * @param options         유료 옵션 합
 * @param total           base + options
 * @param platformFee     round(total × platform_fee_rate)
 * @param durationMinutes 카테고리 기본 소요 (app_settings.duration_hours × 60)
 * PricingService.quote 가 만들고 BookingService 가 견적 응답과 Booking.create 에 쓴다. 금액은 모두 원(long). HTTP 응답으로 직접 나가지 않는다.
 */
public record PriceQuote(long base, long options, long total, long platformFee, int durationMinutes) {
    // 매니저 몫 = total − 플랫폼 수수료. 서버 코드는 예약 행에 고정된 값으로 계산하는 Booking.managerPay() 를 쓰고,
    // 이 메서드는 PricingServiceTest 가 계산 확인에 쓴다
    public long managerPay() {
        return total - platformFee;
    }
}
