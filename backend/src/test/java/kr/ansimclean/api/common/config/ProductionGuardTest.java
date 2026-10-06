package kr.ansimclean.api.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 운영 기동 가드 (감사 M-10) — 외부 호출 없이 설정만 본다 */
// ProductionGuard 는 local·test 가 아닌 프로필에서만 빈으로 만들어지므로 여기서는 직접 new 로 만들어 check()/problems() 를 부른다.
// MockEnvironment: 스프링 설정값(Environment)을 흉내 내는 테스트용 객체. 문자·푸시 모드는 여기에 넣는다.
class ProductionGuardTest {
    /**
     * LiveKit 모드와 CORS 허용 출처(쉼표로 구분)만 바꿔 가며 쓰는 설정 묶음.
     * 나머지(Supabase·LiveKit 주소와 키·녹화용 S3·관리자 토큰 빈 값)는 가드를 통과하는 가짜 값으로 고정한다
     */
    static AppProperties props(String livekitMode, String origins) {
        return new AppProperties(new AppProperties.Cors(List.of(origins.split(","))),
                new AppProperties.Supabase("https://x.supabase.co", "service-role", "jwks", null),
                new AppProperties.Livekit(livekitMode, "wss://x.livekit.cloud", "key", "secret", null, null),
                new AppProperties.Storage("https://x.supabase.co/storage/v1/s3", "ak", "sk", "ap-northeast-1", "recordings"),
                new AppProperties.Admin(""));
    }

    // 문자·푸시·LiveKit 이 모두 logging(로그로만 도는 대역)이면 check() 가 예외를 던지고, 메시지에 세 설정 이름이 다 들어 있다
    @Test
    void 로그_대역이면_기동하지_않는다() {
        MockEnvironment env = new MockEnvironment().withProperty("app.sms.provider", "logging").withProperty("app.push.mode", "logging");
        ProductionGuard guard = new ProductionGuard(env, props("logging", "https://localhost"));
        assertThatThrownBy(guard::check).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_PROVIDER").hasMessageContaining("PUSH_MODE").hasMessageContaining("LIVEKIT_MODE");
    }

    // 나머지는 다 맞고 CORS 에 http:// 개발 서버 출처만 섞였을 때 → 문제는 정확히 하나(singleElement), ALLOWED_ORIGINS
    @Test
    void 개발_서버_origin_이_섞이면_기동하지_않는다() {
        MockEnvironment env = new MockEnvironment().withProperty("app.sms.provider", "solapi").withProperty("app.push.mode", "fcm");
        assertThat(ProductionGuard.problems(env, props("cloud", "capacitor://localhost,http://localhost:5173"))).singleElement().asString().contains("ALLOWED_ORIGINS");
    }

    @Test
    void 실제_서비스_설정이_다_있으면_통과한다() {
        MockEnvironment env = new MockEnvironment().withProperty("app.sms.provider", "solapi").withProperty("app.push.mode", "fcm");
        new ProductionGuard(env, props("cloud", "capacitor://localhost,https://localhost,ionic://localhost")).check(); // 예외가 없으면 통과
    }
}
