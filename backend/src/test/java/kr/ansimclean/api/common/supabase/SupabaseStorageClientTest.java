package kr.ansimclean.api.common.supabase;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Storage REST: 서명 URL 은 base 를 붙여 돌려주고, 없는 파일은 NOT_FOUND(녹화·사진 어느 쪽 문구도 아님), 삭제 404 는 무시 */
// MockRestServiceServer 가 RestClient 의 요청을 가로채 미리 적어 둔 응답을 돌려준다(실제 네트워크 없음).
// 필드로 만들어 두면 JUnit 이 테스트마다 새 객체를 만들므로 테스트마다 새 가짜 서버가 생긴다. 주소·키는 가짜 값이다.
class SupabaseStorageClientTest {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build(); // 이 빌더로 만든 클라이언트의 요청이 가짜 서버로 간다
    SupabaseStorageClient client = new SupabaseStorageClient(new AppProperties(new AppProperties.Cors(List.of()), new AppProperties.Supabase("https://sb.example", "srk", "secret", "s"),
            null, null, null), builder);

    // POST /object/sign/<버킷>/<경로> 에 service-role 키를 싣는다. Supabase 가 돌려준 상대 경로(signedURL) 앞에
    // '<주소>/storage/v1' 을 붙여 앱이 바로 열 수 있는 전체 주소로 만든다
    @Test
    void 서명_URL() {
        server.expect(requestTo("https://sb.example/storage/v1/object/sign/recordings/abc.mp4")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer srk"))
                .andRespond(withSuccess("{\"signedURL\":\"/object/sign/recordings/abc.mp4?token=t\"}", MediaType.APPLICATION_JSON));
        assertThat(client.signedUrl("recordings", "abc.mp4", 3600)).isEqualTo("https://sb.example/storage/v1/object/sign/recordings/abc.mp4?token=t");
        server.verify();
    }

    // 서명 요청이 404(파일 없음)면 중립 NOT_FOUND 를 던지고, 삭제가 404(이미 없음)면 예외 없이 끝난다.
    // 녹화 영상 문구(RECORDING_NOT_READY)는 녹화본을 부르는 LiveService 만 붙인다 — 수리 사진에 녹화 문구가 나가지 않게 (문제 목록 21번)
    @Test
    void 없는_파일은_중립_NOT_FOUND_삭제_404_는_무시() {
        server.expect(requestTo("https://sb.example/storage/v1/object/sign/recordings/nope.mp4")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://sb.example/storage/v1/object/recordings/gone.mp4")).andExpect(method(HttpMethod.DELETE)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.signedUrl("recordings", "nope.mp4", 60)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
        client.delete("recordings", "gone.mp4");
        server.verify();
    }

    // 서명 응답에 signedURL 이 없으면 UPSTREAM 의 기본 문구('잠시 후 다시 시도해주세요') — 예전의 '녹화 영상 주소를 만들지 못했어요' 는 사진에도 나갔다
    @Test
    void 서명_응답이_비면_중립_UPSTREAM() {
        server.expect(requestTo("https://sb.example/storage/v1/object/sign/repair-photos/a.jpg")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.signedUrl("repair-photos", "a.jpg", 60)).isInstanceOf(ApiException.class)
                .hasMessage(ErrorCode.UPSTREAM.defaultMessage()).extracting("code").isEqualTo(ErrorCode.UPSTREAM);
        server.verify();
    }
}
