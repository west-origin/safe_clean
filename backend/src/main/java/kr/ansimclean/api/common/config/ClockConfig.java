package kr.ansimclean.api.common.config;

import kr.ansimclean.api.common.time.AppClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버 전체가 쓰는 시계(AppClock, 서울 시간대) 빈을 하나 등록한다.
 * 서비스·스케줄러는 '지금' 을 이 빈에서 읽는다 — 날짜 경계(오늘·내일)를 서버 시계의 시간대와 상관없이 서울 기준으로 맞춘다.
 * 통합 테스트(예: MatchingIT)는 MutableClock 을 감싼 테스트용 AppClock 빈을 {@code @Primary} 로 따로 등록해
 * 이 빈 대신 쓰고, 시각을 옮겨 가며 검사한다.
 * {@code @Configuration}: 스프링 설정 클래스. 안의 {@code @Bean} 메서드가 돌려준 객체를 스프링이 한 번 만들어 필요한 곳에 넣어 준다.
 */
@Configuration
public class ClockConfig {
    /** 실제 시스템 시계 (Asia/Seoul) */
    @Bean
    public AppClock appClock() {
        return AppClock.system();
    }
}
