package kr.ansimclean.api.offer;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 오퍼 상태 전이 (§6.2-3): 수락은 pending·만료 전에만, 만료·취소·대체는 pending/accepted 에만 */
// Offer 엔티티 단위 테스트 (DB·스프링 없음). 객체를 만들어 상태 메서드를 직접 부르고,
// 허용되지 않는 전이는 ApiException 의 code 값이나 '상태가 그대로인지' 로 확인한다.
// assertThatThrownBy(() -> …) = 그 코드가 예외를 던지는지, extracting("code") = 던진 예외의 code 필드를 꺼내 비교
class OfferTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z");

    /** 새 대기 콜 (예약·매니저 id 는 무작위, 1회차, 76,500원, 일반 콜). 만료 시각만 테스트가 정한다 */
    static Offer pending(Instant expiresAt) {
        return Offer.pending(UUID.randomUUID(), UUID.randomUUID(), 1, 76500, expiresAt, false, NOW);
    }

    @Test
    void 수락은_pending_이고_만료_전일_때만() {
        Offer o = pending(NOW.plusSeconds(60));
        o.accept(NOW);
        assertThat(o.getStatus()).isEqualTo(Offer.ACCEPTED);
        assertThat(o.getRespondedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> o.accept(NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN); // 이미 accepted — 두 번째 수락은 OFFER_TAKEN
    }

    @Test
    void 만료된_오퍼_수락은_OFFER_EXPIRED() {
        Offer o = pending(NOW.minusSeconds(1));
        assertThatThrownBy(() -> o.accept(NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
    }

    // 세 경우: cancelled 콜은 expire·supersede 해도 그대로 / accepted 콜은 cancel 로는 안 바뀌고 supersede 로만 superseded /
    // 만료 시각이 지난 대기 콜은 expire 로 expired 가 되고(true), 그 뒤의 거절은 OFFER_EXPIRED
    @Test
    void 취소_만료_대체는_해당_상태에서만_바뀐다() {
        Offer o = pending(NOW.plusSeconds(60));
        o.cancel();
        assertThat(o.getStatus()).isEqualTo(Offer.CANCELLED);
        assertThat(o.expire(NOW)).isFalse();
        o.supersede();
        assertThat(o.getStatus()).isEqualTo(Offer.CANCELLED);

        Offer accepted = pending(NOW.plusSeconds(60));
        accepted.accept(NOW);
        accepted.cancel();
        assertThat(accepted.getStatus()).isEqualTo(Offer.ACCEPTED);
        accepted.supersede();
        assertThat(accepted.getStatus()).isEqualTo(Offer.SUPERSEDED);

        Offer stale = pending(NOW.minusSeconds(1));
        assertThat(stale.expire(NOW)).isTrue();
        assertThat(stale.getStatus()).isEqualTo(Offer.EXPIRED);
        assertThatThrownBy(() -> stale.reject("개인 사정", NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
    }

    // 닫힌 콜의 거절은 닫힌 이유대로 답한다: 내가 수락한 콜 = INVALID_STATE(일정에서 확인), 다른 매니저 수락·고객 취소(cancelled)·
    // 일정 변경·방문 취소(superseded) = OFFER_TAKEN, 만료 시각이 지난 대기 콜 = OFFER_EXPIRED. 어느 경우든 상태는 그대로다
    @Test
    void 닫힌_콜의_거절은_닫힌_이유대로_오류를_준다() { // 문제 목록 10번 (사용자 결정 B)
        Offer mine = pending(NOW.plusSeconds(60));
        mine.accept(NOW);
        assertThatThrownBy(() -> mine.reject("개인 사정", NOW)).isInstanceOf(ApiException.class)
                .hasMessage("이미 수락한 콜이에요. 일정에서 확인해주세요").extracting("code").isEqualTo(ErrorCode.INVALID_STATE);
        assertThat(mine.getStatus()).isEqualTo(Offer.ACCEPTED);

        Offer cancelled = pending(NOW.plusSeconds(60));
        cancelled.cancel();
        assertThatThrownBy(() -> cancelled.reject("개인 사정", NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);
        mine.supersede();
        assertThatThrownBy(() -> mine.reject("개인 사정", NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_TAKEN);

        Offer late = pending(NOW.minusSeconds(1)); // 만료 시각은 지났지만 스케줄러가 아직 닫지 않았다
        assertThatThrownBy(() -> late.reject("개인 사정", NOW)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OFFER_EXPIRED);
        assertThat(late.getStatus()).isEqualTo(Offer.PENDING);
    }
}
