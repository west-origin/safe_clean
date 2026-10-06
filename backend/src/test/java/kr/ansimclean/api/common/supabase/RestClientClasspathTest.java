package kr.ansimclean.api.common.supabase;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SupabaseAuthClient·SupabaseStorageClient·SolapiSmsSender 는 RestClient.Builder 빈을 주입받는다.
 * Boot 4 에서 그 빈은 spring-boot-starter-restclient(spring-boot-restclient) 가 있어야 만들어진다 — 없으면 슬라이스 테스트는 다 통과해도 실제 서버는 뜨지 않는다.
 * (통합 테스트가 Docker 없이 실행되지 않는 환경에서 그 구멍을 막는 최소 가드)
 */
class RestClientClasspathTest {
    // Class.forName: 이름으로 클래스를 찾는다. 의존성이 빠져 있으면 ClassNotFoundException 이 나서 실패한다
    @Test
    void RestClient_자동_구성이_클래스패스에_있다() {
        assertThatCode(() -> Class.forName("org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration"))
                .as("spring-boot-starter-restclient 의존성이 pom.xml 에 있어야 RestClient.Builder 빈이 생긴다")
                .doesNotThrowAnyException();
    }
}
