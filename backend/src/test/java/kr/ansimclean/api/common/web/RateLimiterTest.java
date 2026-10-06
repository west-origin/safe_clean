package kr.ansimclean.api.common.web;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메모리 속도 제한(RateLimiter)의 키 수 상한 동작을 확인한다: 상한에 차면 새 키를 막지 않고 가장 오래 안 쓰인 키를 버리는지,
 * 자주 쓰이는 키는 버려지지 않고 한도를 넘으면 RATE_LIMITED 로 막히는지.
 */
class RateLimiterTest {
    // 고정 시계(Clock.fixed): 시간이 흐르지 않아 모든 시도가 같은 1시간 창 안에 든다 — 창이 지나 횟수가 저절로 줄지 않는다
    RateLimiter limiter = new RateLimiter(new AppClock(Clock.fixed(Instant.parse("2026-09-24T05:00:00Z"), AppClock.ZONE)));

    // 준비: 키를 상한(MAX_KEYS)까지 한 번씩 채운 뒤 새 키를 넣는다 → 키 수가 상한 그대로인지 본다.
    // 이어서 "busy" 를 9번 쓰고 새 키 1000개로 밀어낸 뒤 → busy 가 남아 10번째는 통과, 11번째는 막히는지 본다
    @Test
    void 키가_상한에_차도_새_키는_받고_가장_오래_쓰이지_않은_키를_버린다() { // 감사 M-05 메모리 상한 + 자체 점검 #12 (예전: 새 키를 전부 막아 전체 로그인 차단)
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) limiter.check("login-ip", "k" + i, 10);
        limiter.check("refresh-ip", "정상 사용자", 60); // 새 키도 통과한다
        assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS);
        limiter.check("login-ip", "k" + (RateLimiter.MAX_KEYS - 1), 10); // 최근 키는 남아 있다

        for (int i = 0; i < 9; i++) limiter.check("login-ip", "busy", 10); // 자주 쓰이는 키는 버려지지 않고 계속 센다
        for (int i = 0; i < 1000; i++) limiter.check("login-ip", "flood" + i, 10); // 새 키 1000개 — 맵 맨 앞(가장 오래 안 쓰인) 키부터 1000개가 버려진다
        limiter.check("login-ip", "busy", 10); // 10번째 — 한도(10) 안이라 통과한다
        // 11번째: 버킷 이름이 otp·verify 로 시작하지 않아 OTP_RATE_LIMITED 가 아니라 RATE_LIMITED
        assertThatThrownBy(() -> limiter.check("login-ip", "busy", 10)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.RATE_LIMITED);
    }
}
