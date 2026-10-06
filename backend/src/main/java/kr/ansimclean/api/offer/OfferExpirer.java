package kr.ansimclean.api.offer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 매니저가 답하지 않은 콜을 닫고 다음 후보를 찾게 하는 백그라운드 작업. 실제 처리는 OfferService.expireDue.
// 콜 목록 API 는 만료 시각이 지난 콜을 이미 빼고 보여 주지만(OfferService.pendingFor),
// DB 상태를 expired 로 바꾸고 다음 매칭을 시작하는 것은 이 스케줄러다.
// 이어서 정지·승인 취소·탈퇴로 수락할 수 없게 된 매니저의 대기 콜도 닫는다(OfferService.closeBlockedOffers, 문제 목록 6번) — 그 콜 때문에 재매칭이 늦어지지 않게.
/** 30초마다: 만료된 pending 오퍼 → expired, 정지·미승인·탈퇴 매니저의 pending 오퍼 → cancelled, 예약에 남은 오퍼가 없으면 재매칭 (BACKEND_PLAN.md §2.5). 스케줄러는 기능 패키지 안에 둔다(B3 판단) */
@Component
public class OfferExpirer {
    private static final Logger log = LoggerFactory.getLogger(OfferExpirer.class);

    private final OfferService offers;

    public OfferExpirer(OfferService offers) {
        this.offers = offers;
    }

    // 30초 간격(fixedDelay, CODE_GUIDE.md 3-9) — 만료 시각이 지난 콜은 늦어도 약 30초 안에 닫히고 다음 매칭이 시작된다.
    // 두 단계는 각각 한 트랜잭션이고 서로 막지 않는다. 한 단계에서 예외가 나면 그 단계의 이번 실행이 모두 되돌려지고 30초 뒤 다시 시도한다
    // (같은 건이 계속 실패하면 다른 콜도 계속 막히니 '오퍼 만료 처리 실패'·'정지 매니저 콜 닫기 실패' 로그를 확인한다)
    @Scheduled(fixedDelay = 30000)
    public void run() {
        try {
            int n = offers.expireDue(); // n = 이번에 expired 로 바꾼 콜 수
            if (n > 0) log.info("오퍼 만료 {}건", n);
        } catch (Exception e) {
            log.error("오퍼 만료 처리 실패", e); // 다음 주기에 다시
        }
        try {
            int n = offers.closeBlockedOffers(); // n = 정지·미승인·탈퇴 매니저라 닫은 콜 수
            if (n > 0) log.info("정지·미승인 매니저 콜 닫음 {}건", n);
        } catch (Exception e) {
            log.error("정지 매니저 콜 닫기 실패", e); // 다음 주기에 다시
        }
    }
}
