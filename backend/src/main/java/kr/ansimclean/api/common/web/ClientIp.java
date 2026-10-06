package kr.ansimclean.api.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 속도 제한용 클라이언트 IP.
 * 기본은 remoteAddr. 앞단 프록시(Fly.io 등) 뒤에 둘 때만 `app.http.client-ip-header` 로 헤더 이름을 준다
 * (Fly 는 `Fly-Client-IP`, 일반 프록시는 `X-Forwarded-For` — 이때는 **마지막** 값, 직전 프록시가 붙인 것).
 * 헤더를 무조건 믿으면 프록시 없이 노출된 서버에서 요청마다 다른 값을 넣어 IP 한도를 우회하고 RateLimiter 키가 무한히 는다.
 * 값은 IP 문자 집합·길이를 검사하고 아니면 remoteAddr 로 돌아간다.
 * 부르는 곳: AuthController (예: 로그인·가입) — 결과를 AuthService 의 IP 한도 키로 넘긴다.
 * 설정값은 application.yml 의 app.http.client-ip-header (환경변수 CLIENT_IP_HEADER, 비어 있으면 헤더를 보지 않는다).
 */
@Component
public class ClientIp {
    private static final Pattern IP = Pattern.compile("[0-9A-Fa-f.:]{1,45}"); // 숫자·16진 글자·점·콜론만, 45자 이하 (IPv6 문자열 최대 길이)

    private final String header;

    /** {@code @Value}: 설정값을 생성자 인자로 받는다. 콜론 뒤가 기본값이라 설정이 없으면 빈 문자열 */
    public ClientIp(@Value("${app.http.client-ip-header:}") String header) {
        this.header = header == null ? "" : header.trim();
    }

    /** 이 요청의 속도 제한 키 (IPv4 주소 그대로, IPv6 는 /64 묶음 — rateKey 참고) */
    public String of(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                String[] parts = value.split(",");
                String candidate = parts[parts.length - 1].trim();
                if (IP.matcher(candidate).matches()) return rateKey(candidate);
            }
        }
        return rateKey(request.getRemoteAddr());
    }

    /**
     * IPv6 는 /64 로 묶는다 — 가정·폰 한 대가 보통 /64 하나를 통째로 받아, 주소마다 새 키를 만들면 IP 한도가 없는 셈이었다 (자체 점검 #12).
     * IPv4(또는 IPv4-mapped) 와 읽을 수 없는 값은 그대로. ':' 가 있을 때만 파싱한다(글자만 있는 값으로 DNS 조회가 나가지 않게)
     */
    static String rateKey(String ip) {
        if (ip == null || !ip.contains(":")) return ip;
        try {
            // '[::1]' 처럼 대괄호로 싼 표기도 있어 벗겨 낸 뒤 파싱한다
            java.net.InetAddress a = java.net.InetAddress.getByName(ip.replace("[", "").replace("]", ""));
            if (!(a instanceof java.net.Inet6Address)) return a.getHostAddress(); // IPv4-mapped(::ffff:1.2.3.4)는 Java 가 IPv4 로 돌려준다
            byte[] b = a.getAddress();
            StringBuilder s = new StringBuilder();
            // IPv6 주소는 16바이트. 앞 8바이트(2바이트씩 4묶음 = 64비트)만 16진으로 적고 뒤는 '::/64' 로 줄인다
            for (int i = 0; i < 8; i += 2) s.append(Integer.toHexString(((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF))).append(':');
            return s.append(":/64").toString();
        } catch (java.net.UnknownHostException e) {
            return ip;
        }
    }
}
