package kr.ansimclean.api.common.supabase;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Supabase Auth 호출 형식과 오류 매핑. 실제 서버 대신 MockRestServiceServer */
// MockRestServiceServer: RestClient 가 보내는 HTTP 요청을 실제 네트워크 대신 가로챈다.
// server.expect(…) 로 '이런 요청이 올 것' 과 돌려줄 응답을 미리 적고, server.verify() 로 적은 요청이 다 왔는지 확인한다.
// 주소·키·번호·비밀번호는 모두 테스트용 가짜 값이다.
class SupabaseAuthClientTest {
    static final String BASE = "https://test.supabase.local/auth/v1"; // 클라이언트가 Supabase 주소 뒤에 /auth/v1 을 붙여 부른다
    static final String KEY = "service-role-key";
    static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    MockRestServiceServer server;
    SupabaseAuthClient client;

    // 테스트마다 새 빌더·가짜 서버·클라이언트를 만든다 (앞 테스트의 기대치가 남지 않게)
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // bindTo(builder): 이 빌더로 만든 RestClient 의 요청이 가짜 서버로 간다. 그래서 클라이언트를 이 빌더로 만든다
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SupabaseAuthClient(new AppProperties(new AppProperties.Cors(List.of()), new AppProperties.Supabase("https://test.supabase.local", KEY, "secret", "s"), null, null, null), builder);
    }

    // POST /admin/users 에 service-role 키(apikey·Authorization 두 헤더)와 번호·phone_confirm=true 를 보내고,
    // 응답 JSON 의 id 를 UUID 로 돌려준다
    @Test
    void 사용자_생성은_admin_헤더와_phone_confirm_을_보내고_id_를_돌려준다() {
        server.expect(requestTo(BASE + "/admin/users")).andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", KEY)).andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(jsonPath("$.phone").value("+821012345678")).andExpect(jsonPath("$.phone_confirm").value(true))
                .andRespond(withSuccess("{\"id\":\"" + USER + "\",\"phone\":\"821012345678\"}", MediaType.APPLICATION_JSON));
        assertThat(client.createUser("+821012345678", "password1")).isEqualTo(USER);
        server.verify();
    }

    // Supabase 가 422 + error_code "phone_exists" 로 답하면 우리 오류 코드 PHONE_TAKEN 으로 바꿔 던진다
    @Test
    void 이미_있는_번호는_PHONE_TAKEN() {
        server.expect(requestTo(BASE + "/admin/users"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body("{\"code\":422,\"error_code\":\"phone_exists\",\"msg\":\"Phone number already registered\"}"));
        assertThatThrownBy(() -> client.createUser("+821012345678", "password1"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PHONE_TAKEN);
    }

    // MockRestServiceServer 는 기대치를 요청 전에 모두 등록해야 한다 (순서대로 소비된다)
    @Test
    void 로그인_성공은_세션을_돌려주고_실패는_LOGIN_FAILED() {
        server.expect(requestTo(BASE + "/token?grant_type=password")).andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", KEY)).andExpect(jsonPath("$.phone").value("+821012345678"))
                .andRespond(withSuccess("{\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":3600,\"user\":{\"id\":\"" + USER + "\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/token?grant_type=password"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error_code\":\"invalid_credentials\"}"));

        SupabaseAuthClient.Session s = client.passwordGrant("+821012345678", "password1");
        assertThat(s.accessToken()).isEqualTo("a");
        assertThat(s.refreshToken()).isEqualTo("r");
        assertThat(s.expiresIn()).isEqualTo(3600);
        assertThat(s.userId()).isEqualTo(USER);
        assertThatThrownBy(() -> client.passwordGrant("+821012345678", "wrong"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.LOGIN_FAILED);
        server.verify();
    }

    // refresh 토큰이 400(없는 토큰)이면 UNAUTHENTICATED(앱은 로그아웃 처리), Supabase 가 500 이면 UPSTREAM(502)
    @Test
    void refresh_실패는_UNAUTHENTICATED_서버_오류는_UPSTREAM() {
        server.expect(requestTo(BASE + "/token?grant_type=refresh_token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body("{\"error_code\":\"refresh_token_not_found\"}"));
        server.expect(requestTo(BASE + "/token?grant_type=refresh_token"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> client.refresh("old"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> client.refresh("old"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.UPSTREAM);
        server.verify();
    }

    // 이미 없는 사용자 삭제(404)는 예외 없이 끝난다. logout 은 Authorization 에 service-role 키가 아니라 사용자 access 토큰을 싣고
    // scope=local(그 세션 하나만)로 부른다. 두 호출 모두 예외가 없고 기대한 요청이 다 왔으면 통과
    @Test
    void 삭제는_404_면_조용히_넘어가고_logout_은_사용자_토큰으로_부른다() {
        server.expect(requestTo(BASE + "/admin/users/" + USER)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{\"error_code\":\"user_not_found\"}"));
        server.expect(requestTo(BASE + "/logout?scope=local")).andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deleteUser(USER);
        client.logout("user-token");
        server.verify();
    }
}
