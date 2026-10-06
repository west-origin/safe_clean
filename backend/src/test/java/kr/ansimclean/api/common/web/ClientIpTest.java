package kr.ansimclean.api.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** 속도 제한 키. 프록시 헤더는 설정으로 켠 경우에만 믿는다 — 직접 노출된 서버에서 헤더를 믿으면 요청마다 다른 값으로 IP 한도를 우회한다 */
// 단위 테스트: 스프링을 띄우지 않고 ClientIp 를 직접 만든다. 생성자 인자 = 믿을 헤더 이름(설정 app.http.client-ip-header).
// 확인하는 것: ClientIp.of(요청) 가 돌려주는 키 — 헤더 값(마지막 값)을 쓰는 경우와 remoteAddr 로 돌아가는 경우, IPv6 의 /64 묶음.
class ClientIpTest {
    /** 가짜 요청(MockHttpServletRequest). 접속 주소(remoteAddr)는 늘 10.0.0.1 이고, header 가 null 이 아니면 그 헤더를 하나 붙인다 */
    static MockHttpServletRequest request(String header, String value) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        if (header != null) req.addHeader(header, value);
        return req;
    }

    // 헤더 이름을 빈 문자열·null 로 주면(설정 없음) 요청에 프록시 헤더가 있어도 무시하고 접속 주소를 쓴다
    @Test
    void 헤더_설정이_없으면_X_Forwarded_For_가_있어도_remoteAddr() {
        assertThat(new ClientIp("").of(request("X-Forwarded-For", "1.2.3.4"))).isEqualTo("10.0.0.1");
        assertThat(new ClientIp(null).of(request("Fly-Client-IP", "1.2.3.4"))).isEqualTo("10.0.0.1");
    }

    // X-Forwarded-For 는 "클라이언트, 프록시1, 프록시2" 처럼 쉼표로 쌓인다. 앞쪽 값은 클라이언트가 마음대로 넣을 수 있어
    // 마지막 값(바로 앞 프록시가 붙인 것)만 쓰고, 앞뒤 공백은 잘라 낸다
    @Test
    void 설정한_헤더의_마지막_값을_쓴다_프록시가_붙인_것() {
        ClientIp xff = new ClientIp("X-Forwarded-For");
        assertThat(xff.of(request("X-Forwarded-For", "1.2.3.4, 5.6.7.8 , 203.0.113.9"))).isEqualTo("203.0.113.9");
        // IPv6 는 앞 64비트만 남긴다. 0 묶음을 줄여 쓰지 않아 "2001:db8:0:0::/64" 모양이 된다 (ClientIp.rateKey)
        assertThat(new ClientIp("Fly-Client-IP").of(request("Fly-Client-IP", "2001:db8::1"))).isEqualTo("2001:db8:0:0::/64");
        assertThat(xff.of(request("Fly-Client-IP", "1.2.3.4"))).isEqualTo("10.0.0.1"); // 다른 헤더는 안 본다
    }

    // 같은 /64 안의 서로 다른 두 주소가 같은 키가 되는지 → IPv4-mapped 는 IPv4 로 바뀌고, IPv4·':' 없는 값은 그대로인지 본다
    @Test
    void IPv6_는_64_접두사로_묶는다_주소마다_새_키를_만들지_못하게() { // 자체 점검 #12
        ClientIp fly = new ClientIp("Fly-Client-IP");
        assertThat(fly.of(request("Fly-Client-IP", "2001:db8:aa:bb:1111:2222:3333:4444")))
                .isEqualTo(fly.of(request("Fly-Client-IP", "2001:db8:aa:bb:ffff::9"))).isEqualTo("2001:db8:aa:bb::/64");
        assertThat(fly.of(request("Fly-Client-IP", "::ffff:203.0.113.9"))).isEqualTo("203.0.113.9"); // IPv4-mapped 는 IPv4 로
        assertThat(fly.of(request("Fly-Client-IP", "203.0.113.9"))).isEqualTo("203.0.113.9");
        assertThat(fly.of(request("Fly-Client-IP", "cafe"))).isEqualTo("cafe"); // ':' 가 없으면 파싱하지 않는다(DNS 조회 없음)
    }

    // 헤더 값은 숫자·16진 글자·점·콜론 45자 이하만 받는다 (ClientIp 의 IP 패턴). 어긋나면 헤더를 버리고 접속 주소로 돌아간다
    @Test
    void 값이_IP_모양이_아니거나_비면_remoteAddr() {
        ClientIp xff = new ClientIp("X-Forwarded-For");
        assertThat(xff.of(request("X-Forwarded-For", "  "))).isEqualTo("10.0.0.1"); // 공백뿐
        assertThat(xff.of(request("X-Forwarded-For", "evil<script>"))).isEqualTo("10.0.0.1"); // 허용하지 않는 글자
        assertThat(xff.of(request("X-Forwarded-For", "a".repeat(60)))).isEqualTo("10.0.0.1"); // 글자는 맞지만 45자를 넘는다
        assertThat(xff.of(request(null, null))).isEqualTo("10.0.0.1"); // 헤더가 아예 없다
    }
}
