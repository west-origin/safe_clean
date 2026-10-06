package kr.ansimclean.api.common.sms;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SolapiSmsSender 생성자 검사: SMS_PROVIDER=solapi 인데 키·비밀키·발신번호 중 하나라도 비면 IllegalStateException 으로
// 서버 기동을 멈추는지 본다. 문자는 보내지 않는다(생성자만 부른다). 키·번호는 가짜 값이다.
class SolapiSmsSenderTest {
    // 첫 줄: API 키가 빈 글자 → 메시지에 SMS_API_KEY. 둘째 줄: 발신번호가 null
    @Test
    void 키가_비어_있으면_기동_때_막는다() {
        assertThatThrownBy(() -> new SolapiSmsSender(new SmsProperties("solapi", "", "secret", "01012345678"), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SMS_API_KEY");
        assertThatThrownBy(() -> new SolapiSmsSender(new SmsProperties("solapi", "key", "secret", null), RestClient.builder()))
                .isInstanceOf(IllegalStateException.class);
    }
}
