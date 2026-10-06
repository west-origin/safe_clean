package kr.ansimclean.api.booking;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 내 예약 카드의 "녹화 영상 보기"(§9.2-8) 에 쓰는 조회. live 패키지가 구현한다(booking 은 live 를 모른다 — 의존 방향 §2.4).
 * 부르는 곳: BookingService — 예약 목록과 예약 한 건 응답을 만들 때 완료(done) 예약만 넘겨 recordingReady 값을 정한다.
 * 구현: live/LiveSessionService(@Primary, 실제 조회)와 NoopRecordingLookup(항상 빈 값). 스프링은 @Primary 쪽을 넣어 준다.
 */
public interface RecordingLookup {
    /** 녹화본이 준비된 예약 id 들 */
    // bookingIds 중 녹화본이 준비된 것만 골라 돌려준다. 여러 건을 한 번에 물어 예약 목록에서 예약마다 따로 조회하지 않는다
    Set<UUID> readyBookingIds(Collection<UUID> bookingIds);
}
