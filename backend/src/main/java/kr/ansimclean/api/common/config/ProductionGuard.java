package kr.ansimclean.api.common.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영 기동 가드 (감사 M-10). local·test 가 아닌 프로필(운영, 또는 프로필을 빠뜨린 배포)에서는 로그 대역을 허용하지 않고,
 * 실제 외부 서비스 설정이 비었으면 **기동을 멈춘다**. health 는 UP 인데 문자·영상·푸시가 조용히 로그로만 도는 배포를 막는다.
 * LoggingSmsSender 는 인증번호 원문을 로그에 쓰므로 운영에서 켜지면 OTP 가 로그에 남는다.
 * {@code @Profile("!local & !test")}: local 도 test 도 아닌 프로필로 떴을 때만 이 빈을 만든다(= 그때만 검사가 돈다).
 * 검사 목록은 problems() 한 곳에 있고, ProductionGuardTest 가 이 메서드를 직접 불러 확인한다.
 */
@Component
@Profile("!local & !test")
public class ProductionGuard {
    private final Environment env;
    private final AppProperties props;

    public ProductionGuard(Environment env, AppProperties props) {
        this.env = env;
        this.props = props;
    }

    /**
     * 빈이 만들어진 직후 한 번 돈다({@code @PostConstruct}). 문제가 하나라도 있으면 예외를 던져 스프링 기동 자체를 실패시킨다.
     * 예외 메시지에는 찾은 문제가 " / " 로 이어져 한 번에 모두 보인다.
     */
    @PostConstruct
    void check() {
        List<String> problems = problems(env, props);
        if (!problems.isEmpty()) throw new IllegalStateException("운영 설정 오류 — 기동하지 않는다: " + String.join(" / ", problems));
    }

    /**
     * 운영 설정의 문제를 한국어 문장 목록으로 돌려준다 (없으면 빈 목록). 값 자체(키·비밀)는 메시지에 넣지 않는다.
     * 문자·푸시 설정은 AppProperties 에 없어서 Environment(스프링이 읽은 전체 설정)에서 이름으로 꺼낸다.
     */
    static List<String> problems(Environment env, AppProperties props) {
        List<String> out = new ArrayList<>();
        if (!"solapi".equals(env.getProperty("app.sms.provider"))) out.add("SMS_PROVIDER 는 solapi 여야 한다(logging 은 인증번호를 로그에 쓴다)");
        if (!"fcm".equals(env.getProperty("app.push.mode"))) out.add("PUSH_MODE 는 fcm 이어야 한다");
        // LiveKit: cloud 가 아니면 문제 하나로 적고, cloud 일 때만 URL·키를 더 본다
        AppProperties.Livekit lk = props.livekit();
        if (lk == null || !"cloud".equals(lk.mode())) out.add("LIVEKIT_MODE 는 cloud 여야 한다(logging 은 카메라가 붙지 않는다)");
        else {
            if (blank(lk.url()) || !lk.url().startsWith("wss://")) out.add("LIVEKIT_URL 은 wss:// 여야 한다");
            if (blank(lk.apiKey()) || blank(lk.apiSecret())) out.add("LIVEKIT_API_KEY·SECRET 이 비었다");
        }
        AppProperties.Supabase sb = props.supabase();
        if (sb == null || blank(sb.url()) || !sb.url().startsWith("https://")) out.add("SUPABASE_URL 은 https:// 여야 한다");
        if (sb == null || blank(sb.serviceRoleKey())) out.add("SUPABASE_SERVICE_ROLE_KEY 가 비었다");
        AppProperties.Storage st = props.storage();
        if (st == null || blank(st.s3Endpoint()) || !st.s3Endpoint().startsWith("https://")) out.add("녹화 업로드용 S3 endpoint 는 https:// 여야 한다");
        if (st == null || blank(st.s3AccessKey()) || blank(st.s3SecretKey())) out.add("녹화 업로드용 S3 키가 비었다");
        // 관리자 토큰: 비워 두는 것(관리 API 닫힘)은 허용하고, 값이 있으면 32자 이상만 받는다
        String admin = props.admin() == null ? null : props.admin().token();
        if (!blank(admin) && admin.length() < 32) out.add("ADMIN_TOKEN 은 비우거나(관리 API 닫힘) 32자 이상이어야 한다");
        // CORS: 목록이 비었거나, 아무 출처나 허용(*)하거나, 암호화되지 않은 http:// 출처(개발 서버용)가 섞였으면 막는다
        List<String> origins = props.cors() == null ? List.of() : props.cors().allowedOrigins();
        if (origins.isEmpty()) out.add("ALLOWED_ORIGINS 가 비었다");
        if (origins.stream().anyMatch(o -> o.equals("*") || o.startsWith("http://"))) {
            out.add("ALLOWED_ORIGINS 에 * 나 http:// 가 있다(운영은 capacitor://localhost·https://localhost·ionic://localhost 만, .env.example)");
        }
        return out;
    }

    /** null 이거나 공백뿐이면 true */
    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
