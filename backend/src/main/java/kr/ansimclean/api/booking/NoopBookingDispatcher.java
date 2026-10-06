package kr.ansimclean.api.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

// BookingDispatcher(예약 → 매칭으로 나가는 문)의 '아무것도 안 하는' 구현. 로그 한 줄씩만 남기고 오퍼·푸시는 만들지 않는다.
// 이 클래스와 OfferService 가 둘 다 스프링 빈으로 등록되지만, 주입할 때는 @Primary 가 붙은 OfferService 가 쓰인다.
// 그래서 지금 서버에서 실제로 불리는 것은 OfferService 쪽이다. 매칭 기능(B4)이 생기기 전 단계(B3)에 쓰던 구현이다.
// 아래 메서드들의 뜻은 BookingDispatcher 의 같은 이름 메서드 설명을 따른다 — 여기서는 로그만 남긴다.
/** B4 전 기본 구현: 로그만. offer/OfferService 가 @Primary 로 대체한다 */
@Component
public class NoopBookingDispatcher implements BookingDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NoopBookingDispatcher.class);

    @Override
    public void dispatch(Booking booking, Set<UUID> excludeManagerIds) {
        log.info("매칭(B4 전) 예약={} 제외={}", booking.getId(), excludeManagerIds.size());
    }

    @Override
    public void dispatchPreferred(Booking booking, UUID managerId) {
        log.info("우선 매칭(B4 전) 예약={} 매니저={}", booking.getId(), managerId);
    }

    @Override
    public void onCancelled(Booking booking) {
        log.info("취소 → 오퍼 정리(B4 전) 예약={}", booking.getId());
    }

    // false = 이전 매니저에게 우선 오퍼를 보내지 않았다 (인터페이스 설명: 부르는 쪽의 푸시 문구가 이 값으로 갈린다)
    @Override
    public boolean onRescheduled(Booking booking, UUID previousManagerId) {
        log.info("일정 변경 → 오퍼 정리(B4 전) 예약={} 이전 매니저={}", booking.getId(), previousManagerId);
        return false;
    }

    @Override
    public void onReleased(Booking booking, UUID releasedManagerId) {
        log.info("매칭 대기(B4 전): 매니저 취소 뒤 재매칭 예약={}", booking.getId());
    }
}
