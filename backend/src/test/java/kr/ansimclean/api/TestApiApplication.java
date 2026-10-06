package kr.ansimclean.api;

import org.springframework.boot.SpringApplication;

/**
 * 로컬 실행용 시작점 (테스트 폴더에 있어 배포 jar 에는 들어가지 않는다).
 * 이 main 을 실행하면 진짜 ApiApplication.main 을 그대로 부르면서 TestcontainersConfiguration 을 더해,
 * Docker 로 띄운 Postgres 에 붙은 서버가 뜬다. ApiApplication.main 을 거치므로 UTC 시간대 고정도 그대로 적용된다.
 */
public class TestApiApplication {

	// SpringApplication.from(원래 main).with(추가 설정): 원래 main 을 실행하되 설정 클래스를 하나 더 얹는다 (Spring Boot 기능)
	public static void main(String[] args) {
		SpringApplication.from(ApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
