package kr.ansimclean.api.catalog;

import kr.ansimclean.api.catalog.dto.AddressSearchResponse;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** GET /catalog/address — 카카오 로컬 API(§13-4)로 실제 주소를 찾는다 */
// 고객 앱 주소 검색 화면(BookingAddressSearch)이 검색어를 바꿀 때마다 부른다. DB 는 쓰지 않는다.
// 두 번 부른다: ① 주소 검색(search/address) — "테헤란로 123" 같은 주소 글자 ② 키워드 검색(search/keyword) — "래미안 아파트" 같은 건물·장소 이름.
//   ① 결과를 먼저, ② 결과 중 같은 주소가 아닌 것을 뒤에 붙여 최대 10건.
// 키(app.address.kakao-rest-key, 환경변수 KAKAO_REST_API_KEY)가 비어 있으면 카카오를 부르지 않고 빈 결과 — 앱은 적은 주소 그대로 진행할 수 있다.
// 행안부 juso API 를 쓰려 했으나 Cloud Run 에서 연결이 안 돼(해외 IP 차단으로 추정) 카카오로 바꿨다 (2026-10-23, 사용자 결정).
// 카카오 주소는 시·도가 "서울"처럼 짧다 — 예약 저장 때 AddressRegion 이 "서울특별시" 로 맞춘다.
// 검색어는 주소라 로그에 남기지 않는다 (CLAUDE.md 11장 11번).
@Service
public class AddressSearchService {
    static final String KAKAO_URL = "https://dapi.kakao.com/v2/local/search";
    static final int MAX_RESULTS = 10;
    private static final Logger log = LoggerFactory.getLogger(AddressSearchService.class);

    private final RestClient client;
    private final String key;

    // 실제 기동 때 쓰는 생성자. 시간 제한(연결 3초·응답 5초)을 여기서 직접 건다 — 공용 설정(spring.http.client)이 이 빌더에 적용되지 않아
    // 연결이 안 될 때 요청이 60초 동안 붙잡혔다 (2026-10-23). 앱은 검색어를 바꿀 때마다 부르므로 짧게 둔다
    @Autowired
    public AddressSearchService(RestClient.Builder builder, @Value("${app.address.kakao-rest-key:}") String key) {
        this(builder.requestFactory(timeoutFactory()), key, true);
    }

    // 테스트용: MockRestServiceServer 에 묶은 빌더를 그대로 쓴다 (requestFactory 를 바꾸면 가짜 서버를 우회하므로)
    AddressSearchService(RestClient.Builder builder, String key, boolean unused) {
        this.client = builder.build();
        this.key = key;
    }

    private static JdkClientHttpRequestFactory timeoutFactory() {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /** 검색어 → 결과 최대 10건. main = 도로명주소(없으면 지번), sub = (동, 건물명)·장소 이름 등. 두 글자 미만이면 빈 결과 */
    public AddressSearchResponse search(String query) {
        String q = query == null ? "" : query.trim();
        if (key.isBlank() || q.length() < 2) return new AddressSearchResponse(q, List.of());

        List<AddressSearchResponse.Result> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(); // 이미 넣은 main — 키워드 결과가 주소 결과와 겹치면 빼려고
        for (JsonNode d : call("address", q)) addAddress(d, out, seen);
        for (JsonNode d : call("keyword", q)) {
            if (out.size() >= MAX_RESULTS) break;
            addPlace(d, out, seen);
        }
        return new AddressSearchResponse(q, out);
    }

    // GET {KAKAO_URL}/{kind}.json?query=…&size=10, 헤더 Authorization: KakaoAK {키}. 응답 { meta, documents: [...] } 의 documents.
    // 키 오류(401)·사용량 초과(429)·연결 실패 모두 UPSTREAM — 화면은 결과 없음처럼 안내하고 직접 입력으로 진행한다
    private JsonNode call(String kind, String q) {
        try {
            JsonNode body = client.get()
                    .uri(KAKAO_URL + "/{kind}.json?query={q}&size={n}", kind, q, MAX_RESULTS)
                    .header("Authorization", "KakaoAK " + key)
                    .retrieve().body(JsonNode.class);
            return body == null ? tools.jackson.databind.node.MissingNode.getInstance() : body.path("documents");
        } catch (RestClientException e) {
            log.warn("카카오 {} 검색 실패: {}", kind, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM, "주소 검색이 잠시 안 돼요. 주소를 직접 적어 진행할 수 있어요");
        }
    }

    // 주소 검색 결과 한 건. address_type 이 REGION(지명만)·ROAD(도로 이름만)이면 번지가 없어 방문 주소가 못 되므로 뺀다.
    // 도로명주소가 있으면 main = 도로명주소, sub = "(동, 건물명)" 이고, 없으면 main = 지번 주소
    private static void addAddress(JsonNode d, List<AddressSearchResponse.Result> out, Set<String> seen) {
        String type = text(d, "address_type");
        if (!type.equals("ROAD_ADDR") && !type.equals("REGION_ADDR")) return;
        JsonNode road = d.path("road_address");
        String jibun = text(d.path("address"), "address_name");
        String main = text(road, "address_name");
        String sub;
        if (main.isEmpty()) {
            main = jibun;
            sub = null;
        } else {
            sub = joinParen(text(d.path("address"), "region_3depth_name"), text(road, "building_name"));
        }
        add(main, main, sub, out, seen);
    }

    // 키워드(장소) 검색 결과 한 건: main = 도로명주소(없으면 지번), sub = 장소 이름 (예: "래미안 아파트")
    private static void addPlace(JsonNode d, List<AddressSearchResponse.Result> out, Set<String> seen) {
        String main = text(d, "road_address_name");
        if (main.isEmpty()) main = text(d, "address_name");
        String name = text(d, "place_name");
        add("p" + text(d, "id"), main, name.isEmpty() ? null : name, out, seen);
    }

    private static void add(String id, String main, String sub, List<AddressSearchResponse.Result> out, Set<String> seen) {
        if (main.isEmpty() || out.size() >= MAX_RESULTS || !seen.add(main)) return;
        out.add(new AddressSearchResponse.Result(id, main, sub));
    }

    // "(역삼동, ABC빌딩)" — 빈 칸은 빼고, 둘 다 비면 null (juso 를 쓸 때와 같은 모양)
    private static String joinParen(String dong, String building) {
        String inner = dong.isEmpty() ? building : building.isEmpty() ? dong : dong + ", " + building;
        return inner.isEmpty() ? null : "(" + inner + ")";
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("").trim();
    }
}
