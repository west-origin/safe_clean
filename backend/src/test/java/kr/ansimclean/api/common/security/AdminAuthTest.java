package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** /admin/** 의 유일한 방어 (§13-20). 슬라이스는 AdminAuth 를 대역으로 두므로 실제 비교는 여기서 */
// AdminAuth.require(헤더 값) 가 설정 토큰과 글자까지 같을 때만 통과하고, 그 밖에는 ApiException(401)을 던지는지 본다.
// 토큰 값은 테스트용 가짜 문자열이다.
class AdminAuthTest {
    /** 관리자 토큰만 채운 설정으로 AdminAuth 를 만든다. null 이면 관리자 설정 자체가 없는 경우 */
    static AdminAuth with(String token) {
        return new AdminAuth(new AppProperties(null, null, null, null, token == null ? null : new AppProperties.Admin(token)));
    }

    @Test
    void 맞는_토큰만_통과한다() {
        AdminAuth auth = with("s3cret");
        assertThatCode(() -> auth.require("s3cret")).doesNotThrowAnyException();
        // 뒤에 공백 하나만 붙어도 다르다 (앞뒤 공백을 잘라 주지 않는다)
        assertThatThrownBy(() -> auth.require("s3cret ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.require("wrong")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.require("")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> auth.require(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void 설정_토큰이_비어_있으면_관리자_API_는_닫힌다() {
        // 설정이 빈 값·공백뿐·없음이면 헤더에 같은 빈 값을 보내도 막힌다 — '빈 값 == 빈 값' 으로 열리지 않게
        assertThatThrownBy(() -> with("").require("")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> with("  ").require("  ")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> with(null).require("anything")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> new AdminAuth(new AppProperties(null, null, null, null, null)).require("x")).isInstanceOf(ApiException.class);
    }
}
