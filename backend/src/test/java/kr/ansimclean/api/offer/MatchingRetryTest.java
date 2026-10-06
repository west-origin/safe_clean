package kr.ansimclean.api.offer;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 자동 재매칭 스케줄러는 실패해도 던지지 않고 다음 시각에 다시 한다 */
// MatchingRetry(자동 재매칭 스케줄러) 단위 테스트. OfferService 는 가짜라 DB 없이 돈다.
// 확인하는 것 두 가지: cron 문자열이 MatchingRetryWindow 의 시각 상수와 같은가, 재매칭이 예외를 내도 run() 이 밖으로 던지지 않는가
class MatchingRetryTest {
    OfferService offers = mock(OfferService.class);

    // 리플렉션(실행 중에 코드 정보를 읽기)으로 run() 에 붙은 Scheduled 의 cron 을 꺼내, 상수로 조립한 "0 5 8-20 * * *" 모양과 비교한다.
    // 한쪽만 바꾸면 여기서 실패한다
    @Test
    void cron_이_안내_문구가_쓰는_실행_시각과_같다() throws Exception { // MatchingRetryWindow 와 어긋나면 '다시 찾아볼게요' 가 거짓이 된다
        String cron = MatchingRetry.class.getMethod("run").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class).cron();
        org.assertj.core.api.Assertions.assertThat(cron).isEqualTo("0 " + kr.ansimclean.api.booking.MatchingRetryWindow.MINUTE + " "
                + kr.ansimclean.api.booking.MatchingRetryWindow.FIRST_HOUR + "-" + kr.ansimclean.api.booking.MatchingRetryWindow.LAST_HOUR + " * * *");
        // 자동 재매칭이 예약을 거르는 기준(약속까지 남은 시간)도 안내 문구 쪽 MIN_LEAD 와 같아야 한다
        org.assertj.core.api.Assertions.assertThat(OfferService.RETRY_MIN_LEAD).isEqualTo(kr.ansimclean.api.booking.MatchingRetryWindow.MIN_LEAD);
    }

    // 가짜 OfferService 가 예외를 던지게 해 두고 run() 을 부른다 → run 이 잡아 로그만 남기므로 예외가 테스트 밖으로 나오지 않고,
    // 재매칭은 실제로 한 번 불렸어야 통과
    @Test
    void 실패해도_던지지_않는다() {
        when(offers.retryFailedMatches()).thenThrow(new IllegalStateException("db down"));
        new MatchingRetry(offers).run();
        verify(offers).retryFailedMatches();
    }

    // 실행마다 밤에 미룬 매칭을 먼저 보내고(첫 실행 08:05) 자동 재매칭을 한다. 미룬 매칭 보내기가 실패해도 자동 재매칭은 돈다 (문제 목록 2번)
    @Test
    void 밤에_미룬_매칭을_먼저_보내고_실패해도_자동_재매칭은_돈다() {
        when(offers.releaseHeldDispatches()).thenThrow(new IllegalStateException("db down"));
        new MatchingRetry(offers).run();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(offers);
        order.verify(offers).releaseHeldDispatches();
        order.verify(offers).retryFailedMatches();
    }
}
