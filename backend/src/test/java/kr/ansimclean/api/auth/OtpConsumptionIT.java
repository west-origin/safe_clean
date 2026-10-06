package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

// 진짜 Postgres 위에서 OtpService.consume 의 조건부 UPDATE(consumeIfUnused)가 동시 요청 중 하나에만 1행을 주는지 본다.
// 같은 토큰으로 가입·재설정이 두 번 진행되지 못하게 하는 장치다. 서버 전체를 띄우지만(@SpringBootTest) HTTP 는 쓰지 않는다
/** 통합 (Docker 필요): 같은 인증 토큰을 두 스레드가 동시에 소비하면 정확히 하나만 이긴다 (감사 H-01). 외부 호출 없음 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class OtpConsumptionIT {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @Autowired OtpService otp;
    @Autowired PhoneVerificationRepository repository;
    @Autowired AppClock clock;

    @Test
    void 같은_토큰_동시_소비는_하나만_성공한다() throws Exception {
        // 준비: 인증번호 확인까지 끝난(succeed) 인증 건을 문자 발송 없이 DB 에 바로 저장한다. 코드 해시 값은 이 테스트에서 쓰지 않는다
        Instant now = clock.now();
        String token = UUID.randomUUID().toString();
        PhoneVerification v = PhoneVerification.issue("+821099999999", "customer", "test-hash", now, now.plusSeconds(300));
        v.succeed(token, now);
        repository.saveAndFlush(v);
        // CountDownLatch(1): 출발 신호. 두 스레드가 await 로 기다리다가 countDown() 한 번에 거의 동시에 출발한다
        CountDownLatch start = new CountDownLatch(1);
        // try(…) 블록이 끝나면 스레드 풀을 닫는다 (자바 21 의 ExecutorService.close 는 남은 작업이 끝나길 기다린다)
        try (var workers = Executors.newFixedThreadPool(2)) {
            // 각 스레드가 할 일: 신호를 기다렸다가 소비 시도 → 이기면 true, 지면 OTP_EXPIRED 인지 확인하고 false
            java.util.concurrent.Callable<Boolean> consume = () -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                try { otp.consume(token, "customer"); return true; }
                catch (ApiException e) {
                    assertThat(e.code()).isEqualTo(ErrorCode.OTP_EXPIRED);
                    return false;
                }
            };
            var first = workers.submit(consume);
            var second = workers.submit(consume);
            start.countDown();
            // 결과는 순서와 상관없이 true 하나, false 하나여야 한다 (get(10초) = 그 스레드의 결과를 최대 10초 기다린다)
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        // DB 에도 사용 시각이 적혔는지
        assertThat(repository.findByToken(token).orElseThrow().getConsumedAt()).isNotNull();
    }
}
