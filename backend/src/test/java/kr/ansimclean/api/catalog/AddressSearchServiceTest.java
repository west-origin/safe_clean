package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.AddressSearchResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 주소 검색 (§13-4): 카카오 로컬 API 요청 형식과 응답 → { query, results: [{ id, main, sub }] } 변환. 실제 서버 대신 MockRestServiceServer */
// 키·주소·장소 id 는 테스트용 가짜 값이다. 응답 모양은 카카오 개발자 문서(로컬 API 주소 검색·키워드 검색)를 따른다.
// 서비스는 주소 검색(address) → 키워드 검색(keyword) 순서로 두 번 부른다.
class AddressSearchServiceTest {
    static final String ADDRESS = AddressSearchService.KAKAO_URL + "/address.json";
    static final String KEYWORD = AddressSearchService.KAKAO_URL + "/keyword.json";

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    AddressSearchService service = new AddressSearchService(builder, "test-key", true);

    static String docs(String array) {
        return "{\"meta\":{\"total_count\":1},\"documents\":" + array + "}";
    }

    // 주소 결과: 도로명주소가 main, "(동, 건물명)" 이 sub. 도로명이 없으면 지번이 main. 지명(REGION)만인 결과는 뺀다.
    // 키워드 결과: 주소 결과와 같은 주소는 빼고, 장소 이름을 sub 로 뒤에 붙인다
    @Test
    void 주소_결과_뒤에_장소_결과를_붙이고_같은_주소는_뺀다() {
        server.expect(requestTo(startsWith(ADDRESS)))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andExpect(queryParam("query", UriUtils.encode("테헤란로 123", StandardCharsets.UTF_8)))
                .andExpect(queryParam("size", "10"))
                .andRespond(withSuccess(docs("["
                        + "{\"address_type\":\"ROAD_ADDR\",\"address\":{\"address_name\":\"서울 강남구 역삼동 1\",\"region_3depth_name\":\"역삼동\"},"
                        + "\"road_address\":{\"address_name\":\"서울 강남구 테헤란로 123\",\"building_name\":\"ABC빌딩\"}},"
                        + "{\"address_type\":\"REGION_ADDR\",\"address\":{\"address_name\":\"서울 강남구 역삼동 2\",\"region_3depth_name\":\"역삼동\"},\"road_address\":null},"
                        + "{\"address_type\":\"REGION\",\"address\":{\"address_name\":\"서울 강남구\"},\"road_address\":null}"
                        + "]"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(KEYWORD)))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(docs("["
                        + "{\"id\":\"11\",\"place_name\":\"ABC빌딩\",\"road_address_name\":\"서울 강남구 테헤란로 123\",\"address_name\":\"서울 강남구 역삼동 1\"},"
                        + "{\"id\":\"22\",\"place_name\":\"래미안 아파트\",\"road_address_name\":\"\",\"address_name\":\"서울 서초구 반포동 3\"}"
                        + "]"), MediaType.APPLICATION_JSON));

        AddressSearchResponse res = service.search("  테헤란로 123 ");

        assertThat(res.query()).isEqualTo("테헤란로 123");
        assertThat(res.results()).containsExactly(
                new AddressSearchResponse.Result("서울 강남구 테헤란로 123", "서울 강남구 테헤란로 123", "(역삼동, ABC빌딩)"),
                new AddressSearchResponse.Result("서울 강남구 역삼동 2", "서울 강남구 역삼동 2", null),
                new AddressSearchResponse.Result("p22", "서울 서초구 반포동 3", "래미안 아파트"));
        server.verify();
    }

    // 키 오류(401)·사용량 초과 등 카카오가 오류로 답하면 UPSTREAM — 화면은 결과 없음처럼 안내하고 직접 입력으로 진행한다
    @Test
    void 카카오가_오류로_답하면_UPSTREAM() {
        server.expect(requestTo(startsWith(ADDRESS))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.search("서울 강남구"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM));
    }

    // 키가 없거나(로컬·테스트) 검색어가 두 글자 미만이면 카카오를 부르지 않는다
    @Test
    void 키가_없거나_검색어가_짧으면_부르지_않는다() {
        AddressSearchService noKey = new AddressSearchService(builder, "", true);
        assertThat(noKey.search("서울 강남구").results()).isEmpty();
        assertThat(service.search("서").results()).isEmpty();
        server.verify(); // 기대한 요청이 없다 = 아무 요청도 가지 않았다
    }

    // 첫 1회 청소 주소 대조: 도로명으로 적든 지번으로 적든 카카오가 같은 필지(address.b_code·본번·부번)를 주면 같은 값
    static final String SAME_PARCEL = docs("[{\"address_type\":\"%s\",\"address\":{\"address_name\":\"서울 강남구 역삼동 825-2\","
            + "\"b_code\":\"1168010100\",\"mountain_yn\":\"N\",\"main_address_no\":\"825\",\"sub_address_no\":\"2\"},"
            + "\"road_address\":{\"address_name\":\"서울 강남구 강남대로 396\"}}]");

    @Test
    void 도로명과_지번이_같은_건물이면_같은_고유값() {
        server.expect(requestTo(startsWith(ADDRESS)))
                .andExpect(queryParam("query", UriUtils.encode("서울특별시 강남구 강남대로 396", StandardCharsets.UTF_8))) // 괄호(동 이름)는 빼고 묻는다
                .andRespond(withSuccess(SAME_PARCEL.formatted("ROAD_ADDR"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(ADDRESS)))
                .andExpect(queryParam("query", UriUtils.encode("서울 강남구 역삼동 825-2", StandardCharsets.UTF_8)))
                .andRespond(withSuccess(SAME_PARCEL.formatted("REGION_ADDR"), MediaType.APPLICATION_JSON));
        String road = service.buildingKey("서울특별시 강남구 강남대로 396 (역삼동)");
        assertThat(road).isEqualTo("1168010100-N-825-2").isEqualTo(service.buildingKey("서울 강남구 역삼동 825-2"));
        server.verify();
    }

    @Test
    void 카카오가_못_찾은_주소는_VALIDATION_장애는_UPSTREAM_키가_없으면_null() {
        server.expect(requestTo(startsWith(ADDRESS))).andRespond(withSuccess(docs("[]"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(ADDRESS))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> service.buildingKey("서울 아무로 99999"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION));
        assertThatThrownBy(() -> service.buildingKey("서울 강남구 강남대로 396"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM));
        assertThat(new AddressSearchService(builder, "", true).buildingKey("서울 강남구 강남대로 396")).isNull();
        server.verify();
    }
}
