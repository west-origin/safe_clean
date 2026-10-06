package kr.ansimclean.api.offer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매시 05분, 08:05~20:05 (Asia/Seoul): ① 밤에 미뤄 둔 매칭을 보내고(OfferService.releaseHeldDispatches — 보통 08:05 첫 실행에서, 문제 목록 2번)
 * ② 매칭 실패로 멈춘 예약을 새 후보에게 다시 보낸다 (OfferService.retryFailedMatches, 사용자 결정 2026-09-27).
 * 밤에는 돌지 않는다 — 매니저에게 '새 콜' 푸시가 간다. 밤에 생긴 새 콜도 OfferService 가 여기까지 미룬다. 예전에는 실패 뒤 저절로 다시 찾는 길이 없었다(자체 점검 #5·#13)
 * 이 cron 과 booking/MatchingRetryWindow 의 시각 상수는 반드시 같아야 한다 — 고객 안내 문구가 그 상수로
 * '다시 찾아볼게요' 를 말할지 정한다 (MatchingRetryTest 가 둘이 같은지 확인한다).
 */
@Component
public class MatchingRetry {
    private static final Logger log = LoggerFactory.getLogger(MatchingRetry.class);

    private final OfferService offers;

    public MatchingRetry(OfferService offers) {
        this.offers = offers;
    }

    // cron = 서울 8~20시 매시 5분 0초 (CODE_GUIDE.md 3-9). 값을 바꾸면 MatchingRetryWindow 상수도 같이 바꾼다.
    // 두 단계는 각각 한 트랜잭션이고 서로 막지 않는다 — 한 단계에서 예외가 나면 그 단계의 이번 실행 전체가 되돌려지고 로그만 남은 채 다음 단계로 간다.
    // 같은 예약이 계속 실패하면 다음 시각에도 전부 함께 되돌려지므로 '밤에 미룬 매칭 보내기 실패'·'자동 재매칭 실패' 로그를 확인한다.
    // 미룬 매칭을 먼저 보낸다 — 그 뒤 바로 실패한 예약도 같은 실행의 자동 재매칭이 본다(새 후보가 없으면 아무것도 안 한다)
    @Scheduled(cron = "0 5 8-20 * * *", zone = "Asia/Seoul")
    public void run() {
        try {
            int held = offers.releaseHeldDispatches(); // held = 밤에 미뤄 둔 매칭을 이번에 보낸 예약 수
            if (held > 0) log.info("밤에 미룬 매칭 보냄 {}건", held);
        } catch (Exception e) {
            log.error("밤에 미룬 매칭 보내기 실패", e); // 다음 시각에 다시
        }
        try {
            int n = offers.retryFailedMatches(); // n = 새 후보에게 다시 콜을 보낸 예약 수
            if (n > 0) log.info("자동 재매칭 {}건", n);
        } catch (Exception e) {
            log.error("자동 재매칭 실패", e); // 다음 시각에 다시
        }
    }
}
