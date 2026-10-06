package kr.ansimclean.api.catalog.dto;

import java.util.List;

/** = 앱 searchAddress(q) → { query, results: [{ id, main, sub }] }. 주소 API(§13-4) 연동 전에는 빈 결과 */
// GET /catalog/address 응답. query 는 앞뒤 공백을 뺀 검색어 (CatalogService.searchAddress)
public record AddressSearchResponse(String query, List<Result> results) {
    /** 검색 결과 한 줄: id, main(첫 줄 주소), sub(둘째 줄). 고객 앱은 고른 결과의 main 을 주소로 쓴다 */
    public record Result(String id, String main, String sub) {}
}
