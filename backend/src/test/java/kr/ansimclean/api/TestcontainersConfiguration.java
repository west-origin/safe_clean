package kr.ansimclean.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 쪽 스프링 설정(@TestConfiguration): Docker 로 Postgres 컨테이너를 띄워 DB 로 쓴다.
 * 쓰는 곳 예: TestApiApplication(로컬 실행). IT 들은 이 설정을 쓰지 않고 자기 클래스의 @Container 필드로 Postgres 를 따로 띄운다.
 * proxyBeanMethods = false: @Bean 메서드끼리 서로 부르지 않으므로 스프링이 감싸는 프록시를 만들지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// @Bean: 이 메서드가 돌려준 객체(컨테이너)를 스프링이 관리한다. 컨테이너는 스프링이 시작할 때 뜨고 끝날 때 내려간다.
	// @ServiceConnection: 그 컨테이너의 주소·계정을 DB 연결 설정(spring.datasource.*)으로 자동으로 넣는다
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")); // IT 들과 같은 이미지 (계획 §B1)
	}

}
