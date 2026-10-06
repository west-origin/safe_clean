package kr.ansimclean.api.booking;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

// RecordingLookup 의 기본 구현: 언제나 '녹화본 준비된 예약 없음'(빈 집합)을 돌려준다.
// live 패키지의 LiveSessionService 도 RecordingLookup 을 구현하고 @Primary 가 붙어 있어, 주입할 때는 그쪽이 쓰인다.
/** live 패키지의 구현(@Primary)이 없을 때: 준비된 녹화본 없음 */
@Component
public class NoopRecordingLookup implements RecordingLookup {
    /** 어떤 예약 id 를 줘도 빈 집합 — 예약 카드에 "녹화 영상 보기" 가 나오지 않는다 */
    @Override
    public Set<UUID> readyBookingIds(Collection<UUID> bookingIds) {
        return Set.of();
    }
}
