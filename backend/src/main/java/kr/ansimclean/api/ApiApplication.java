package kr.ansimclean.api;

import org.springframework.boot.SpringApplication;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.sms.SmsProperties;
import kr.ansimclean.api.push.PushProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

/**
 * 서버(backend)의 시작점. main 이 스프링을 띄우면 두 앱(고객·매니저)이 부르는 HTTP API 가 열린다.
 * {@code @SpringBootApplication}: 이 패키지(kr.ansimclean.api) 아래의 @Component·@Service·@RestController·@Configuration
 * 클래스를 찾아 등록하고, 의존성에 맞는 기본 설정(DB 연결·보안·웹 서버 등)을 자동으로 켠다.
 * {@code @EnableConfigurationProperties}: application.yml 의 app.*, app.sms.*, app.push.* 값을
 * 아래 목록의 record 에 담아 다른 클래스가 주입받을 수 있게 한다. 설정 record 를 자동으로 찾는 설정은 없으므로,
 * 새 설정 record 는 이 목록에 넣어야 등록된다 (빠뜨리면 그것을 받는 빈을 만들지 못해 서버가 뜨지 않는다).
 * 로컬에서 Testcontainers Postgres 로 띄울 때는 test 쪽 TestApiApplication 이 이 main 을 그대로 부른다.
 */
@SpringBootApplication
@EnableConfigurationProperties({ AppProperties.class, SmsProperties.class, PushProperties.class })
public class ApiApplication { // 스케줄링은 SchedulingConfig (test 프로필에서는 꺼진다)

	/**
	 * 서버 실행 (./mvnw spring-boot:run 또는 java -jar). 시간대를 먼저 고정한 뒤 스프링을 시작한다.
	 * 스프링보다 먼저 바꿔야 DB 연결·빈이 만들어질 때부터 UTC 가 적용된다.
	 * 참고: @SpringBootTest 는 기본값으로 main 을 거치지 않으므로 테스트에는 이 설정이 적용되지 않는다.
	 */
	public static void main(String[] args) {
		// LocalTime(service_time·근무시간) 컬럼은 JVM 기본 시간대로 변환돼 저장된다 — 배포 환경(UTC 컨테이너)과 로컬(KST)이 달라지지 않게 고정한다
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ApiApplication.class, args);
	}

}
