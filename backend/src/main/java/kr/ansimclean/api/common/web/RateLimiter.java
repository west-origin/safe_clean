package kr.ansimclean.api.common.web;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메모리 속도 제한 (단일 인스턴스 전제, §13-11 — 여러 대로 늘리기 전에 공유 저장소나 edge 제한으로 바꾼다). 키별로 최근 시각을 들고 창 안의 횟수를 센다.
 * 로그인 시도 시간당 10회, 인증번호 검증 시도 시간당 10회 (번호 기준).
 * 번호 기준만으로는 임의 번호로 문자를 무한히 보내게 할 수 있어 IP·전체 한도를 더한다 (값은 코드에서 정함 — PROGRESS 판단 항목).
 * 쓰는 방법: check(버킷 이름, 키, 한도) — 버킷은 한도의 종류("login", "otp-ip", "booking" 등), 키는 번호·IP·사용자 id.
 * 부르는 곳 예: AuthService(인증번호·로그인 등), BookingService(예약 생성). 정리는 AuthCleaner 가 매일 cleanup() 을 부른다.
 * 서버를 다시 켜면 메모리라 횟수가 모두 사라진다.
 * 번호별 인증번호 발송 한도(OtpService.RESEND_INTERVAL·MAX_PER_HOUR)는 여기가 아니라 OtpService 가 DB 의 발송 기록으로 센다 (재시작해도 남는다).
 * 트랜잭션과 무관한 메모리라, 요청이 뒤에서 실패해 DB 가 되돌려져도 센 횟수는 남는다 (예: BookingService.doCreate 의 설명).
 */
@Component
public class RateLimiter {
    public static final int LOGIN_PER_HOUR = 10; // 같은 번호의 시간당 로그인 시도
    public static final int VERIFY_PER_HOUR = 10; // 같은 번호의 시간당 인증번호 확인 시도
    /** 같은 IP 의 시간당 인증번호 요청 */
    public static final int OTP_PER_IP_PER_HOUR = 20;
    /** 서비스 전체의 시간당 인증번호 발송 (문자 비용 차단기) */
    public static final int OTP_GLOBAL_PER_HOUR = 500;
    /** 같은 IP 의 시간당 로그인 시도 */
    public static final int LOGIN_PER_IP_PER_HOUR = 60;
    /** 같은 계정의 시간당 탈퇴(비밀번호 확인) 시도 */
    public static final int DELETE_PER_USER_PER_HOUR = 5;
    /** 같은 IP 의 시간당 토큰 갱신 — 정상 앱은 1시간에 1~2번 (보안 검토: 훔친·추측한 refresh 토큰을 무한히 시도하지 못하게) */
    public static final int REFRESH_PER_IP_PER_HOUR = 60;
    /** 같은 IP 의 시간당 가입 완료(계정 생성) 시도 — 인증 토큰 추측·대량 가입 차단 */
    public static final int SIGNUP_PER_IP_PER_HOUR = 20;
    /** 같은 IP 의 시간당 인증번호 검증 — 번호를 바꿔 가며 DB 조회·키를 늘리지 못하게 (감사 M-05) */
    public static final int VERIFY_PER_IP_PER_HOUR = 60;
    /** 같은 고객의 시간당 예약 생성 — 예약 한 건이 후보 매니저 전원 오퍼·푸시로 커진다 (감사 M-05) */
    public static final int BOOKING_PER_USER_PER_HOUR = 10;
    /** 같은 고객의 시간당 일정 변경 — 변경 한 번이 후보 전원의 새 콜 푸시로 커진다 (자체 점검 #20) */
    public static final int RESCHEDULE_PER_USER_PER_HOUR = 10;
    /** 같은 사용자의 시간당 문의 등록 (감사 M-05) */
    public static final int INQUIRY_PER_USER_PER_HOUR = 10;
    /**
     * 들고 있을 키 수 상한(메모리를 키 수만큼 늘리지 못하게, 감사 M-05). 넘으면 **가장 오래 쓰이지 않은 키**를 버린다.
     * 예전에는 새 키를 전부 막아, IP 를 바꿔 가며 키를 채운 한 사람이 모든 사용자의 로그인·토큰 갱신을 막을 수 있었다 (자체 점검 #12).
     * 버려진 키는 횟수가 처음부터 다시 센다 — 한도를 우회하려면 자기 시도 사이사이에 10만 개의 새 키(IPv6 는 /64 단위, ClientIp)를 채워야 한다
     */
    static final int MAX_KEYS = 100_000;
    private static final Duration WINDOW = Duration.ofHours(1); // 횟수를 세는 창: 지금부터 1시간 전까지

    // 키("버킷:키") → 그 키로 통과한 시각들 (오래된 것이 앞). Deque 는 앞뒤로 넣고 뺄 수 있는 줄(큐)이다
    // new LinkedHashMap<>(16, 0.75f, true): 마지막 인자 true 가 '접근 순서' — 조회할 때마다 그 키가 맨 뒤로 가서, 맨 앞이 가장 오래 안 쓰인 키가 된다
    /** 접근 순서 LinkedHashMap = LRU. 키 추가·조회·정리는 이 맵을 잠그고, 횟수 세기는 키별 Deque 를 잠근다 */
    private final Map<String, Deque<Instant>> hits = new LinkedHashMap<>(16, 0.75f, true) {
        // 새 키를 넣은 직후 맵이 불러 준다. true 를 돌려주면 맨 앞(가장 오래 안 쓰인) 키를 버린다
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
            return size() > MAX_KEYS;
        }
    };
    private final AppClock clock;

    public RateLimiter(AppClock clock) {
        this.clock = clock;
    }

    /**
     * 한도 안이면 기록하고 통과, 넘으면 429 — 버킷이 otp·verify 로 시작하면(인증번호 요청·확인) OTP_RATE_LIMITED, 나머지는 RATE_LIMITED.
     * 막힌 시도는 세지 않는다
     */
    public void check(String bucket, String key, int limit) {
        Instant now = clock.now();
        String k = bucket + ":" + key;
        Deque<Instant> q;
        // synchronized(객체): 여러 요청 스레드가 동시에 들어와도 그 객체를 잠근 동안에는 한 스레드만 안쪽을 실행한다.
        // 맵 잠금은 키를 찾거나 만드는 짧은 순간만 쥔다 — 서로 다른 키의 횟수 세기가 서로를 기다리지 않게
        synchronized (hits) {
            q = hits.computeIfAbsent(k, x -> new ArrayDeque<>());
        }
        synchronized (q) {
            // 창(1시간)을 벗어난 오래된 시각을 앞에서부터 버린 뒤, 남은 개수로 한도를 판단한다
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) q.pollFirst();
            if (q.size() >= limit) throw new ApiException(bucket.startsWith("otp") || bucket.startsWith("verify") ? ErrorCode.OTP_RATE_LIMITED : ErrorCode.RATE_LIMITED, "시도가 너무 많아요. 잠시 후 다시 시도해주세요");
            q.addLast(now);
        }
    }

    /** 창을 벗어난 키를 버린다 (AuthCleaner 가 매일 부른다 — 메모리가 키 수만큼 자라지 않게) */
    public void cleanup() {
        Instant cutoff = clock.now().minus(WINDOW);
        synchronized (hits) {
            // 비었거나, 가장 최근 시각(peekLast)도 1시간이 지난 키를 지운다 — 남아 있어도 다음 check 에서 모두 버려질 값들이다
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
                }
            });
        }
    }

    /** 지금 들고 있는 키 수 (같은 패키지의 테스트용) */
    int size() {
        synchronized (hits) {
            return hits.size();
        }
    }

    /** 성공하면 카운트를 지운다 (정상 사용자가 예전 실패 때문에 막히지 않게) */
    public void reset(String bucket, String key) {
        synchronized (hits) {
            hits.remove(bucket + ":" + key);
        }
    }
}
