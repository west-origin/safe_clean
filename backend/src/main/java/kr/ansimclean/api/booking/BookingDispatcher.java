package kr.ansimclean.api.booking;

import java.util.Set;
import java.util.UUID;

/**
 * 매칭(콜) 쪽으로 나가는 문 (BACKEND_PLAN.md §6.2). **B4 의 offer/OfferService 가 구현하고 @Primary 를 붙인다.**
 * B3 에서는 NoopBookingDispatcher 가 로그만 남겨 예약이 assigning 에 머문다. 모두 예약과 같은 트랜잭션 안에서 불린다.
 * 밤(서울 21시~08시)에는 구현체가 콜을 보내지 않고 예약에 보류 표시(Booking.holdDispatch)만 남긴 뒤 아침 08:05 에 보낼 수 있다 (문제 목록 2번).
 *
 * booking 패키지는 이 인터페이스만 부르고, 실제 구현(콜 만들기·푸시)은 offer 패키지의 OfferService 에 있다.
 * NoopBookingDispatcher 도 이 인터페이스를 구현하지만 스프링은 @Primary 가 붙은 OfferService 를 넣어 준다.
 * OfferService 가 BookingService·JobService 를 받아 쓰기 때문에, 그 둘은 이 인터페이스를 @Lazy 로 받아 생성자 순환을 피한다.
 */
public interface BookingDispatcher {
    /** 후보 전원에게 오퍼. exclude = 이번에 뺄 매니저 (예: 우선 콜을 거절·만료한 매니저, 방문을 취소한 매니저) */
    // booking 쪽에서 부르는 곳: BookingService.startAssigning (우선 매니저가 없을 때, 빈 exclude). 후보가 없으면 구현체가 매칭 실패로 표시한다
    void dispatch(Booking booking, Set<UUID> excludeManagerIds);

    /** 한 매니저에게 먼저(10분). 거절·만료면 구현체가 dispatch(booking, {managerId}) 로 이어간다 */
    // 부르는 곳: BookingService.startAssigning — 정기청소 회차에 직전 회차 매니저가 있을 때. 10분은 app_settings.preferred_offer_ttl_minutes 의 시드 값
    void dispatchPreferred(Booking booking, UUID managerId);

    /** 고객 취소: pending 오퍼를 cancelled 로 */
    // 부르는 곳: BookingService.cancelOne(고객 취소)과 StaleBookingCleaner(방문일이 지나 시스템 취소)
    void onCancelled(Booking booking);

    /** 일정 변경: accepted → superseded, pending → cancelled, 체크리스트·라이브 세션 리셋, 이전 매니저에게 우선 오퍼. 우선 오퍼를 실제로 보냈으면 true (푸시 문구가 갈린다) */
    // 부르는 곳: BookingService.reschedule — Booking.reschedule 로 날짜를 바꾼 뒤, 바꾸기 전 상태가 requested 가 아니었을 때.
    // 밤이라 미뤘으면 false 이고 booking.isDispatchHeld() 가 true — 이전 매니저 안내(BookingRescheduled)는 부른 쪽이 적지 않고 구현체가 아침에 우선 콜과 함께 적는다
    boolean onRescheduled(Booking booking, UUID previousManagerId);

    /**
     * 매니저가 방문을 취소했다(Booking.releaseByManager 뒤): accepted → superseded(다음 수락이 offers_one_accepted 에 막히지 않게),
     * 새 회차로 올려 처음 콜을 받았던 매니저에게도 다시 보내고, 취소한 매니저만 뺀다 (전체 재점검 O1)
     */
    // 부르는 곳: JobService.doCancelByManager (매니저 앱의 방문 취소)
    void onReleased(Booking booking, UUID releasedManagerId);
}
