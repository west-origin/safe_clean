package kr.ansimclean.api.profile;

import kr.ansimclean.api.common.error.ApiException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 도로명주소 문자열에서 시/도와 시/군/구를 뽑는다 (매칭용, BACKEND_PLAN.md §6.2).
 * 주소 검색 API(§13-4)를 붙이면 API 가 준 값을 쓰고 이 파싱은 대체 수단이 된다.
 *   "서울특별시 강남구 테헤란로 123" → sido=서울특별시, sigungu=강남구
 *   "서울 강남구 …" · "서울시 강남구 …" · "경기 성남시 …" 같은 흔한 축약도 정식 이름으로 맞춘다 (없으면 후보 0명으로 영영 매칭되지 않는다)
 *   "부산광역시 중앙대로 2127-23"   → sido=부산광역시, sigungu=null (구가 없다)
 * 값: sido = 정식 시·도 이름, sigungu = 시·군·구. 못 읽으면 각각 null.
 * 쓰는 곳 예: BookingService(예약 전 requireMatchable, 예약 행에 parse 결과 저장),
 * OfferService(매니저 활동 지역을 canonicalSido 로 맞춰 비교).
 * 예약 쪽은 저장할 때 한 번 정식 이름으로 바꾸고, 매니저 활동 지역은 비교할 때마다 canonicalSido 로 바꾼다
 * — 두 쪽 글자가 같아야 매칭된다.
 */
public record AddressRegion(String sido, String sigungu) {
    // Pattern = 미리 컴파일해 둔 정규식.
    // SIDO: 맨 앞 단어가 특별시·광역시·특별자치시·특별자치도·도 로 끝나고 뒤에 공백이 오면 그 단어를 시·도로 잡는다.
    // SIGUNGU: 시·도를 떼어 낸 나머지의 첫 단어가 시·군·구로 끝나면 그 단어. '성남시 분당구' 처럼 두 단계면 앞의 '성남시' 만 잡는다
    private static final Pattern SIDO = Pattern.compile("^(\\S+?(?:특별시|광역시|특별자치시|특별자치도|도))\\s+");
    private static final Pattern SIGUNGU = Pattern.compile("^(\\S+?(?:시|군|구))(?:\\s|$)");
    /** 축약형·옛 이름 → 정식 이름 (행정안전부 현행 명칭) */
    // '강원도'·'전라북도'·'제주도' 처럼 '도' 로 끝나는 옛 이름은 SIDO 정규식에 먼저 잡힌 뒤 canonicalSido 에서 이 표로 바뀐다
    private static final Map<String, String> CANONICAL = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("서울시", "서울특별시"),
            Map.entry("부산", "부산광역시"), Map.entry("부산시", "부산광역시"),
            Map.entry("대구", "대구광역시"), Map.entry("대구시", "대구광역시"),
            Map.entry("인천", "인천광역시"), Map.entry("인천시", "인천광역시"),
            Map.entry("광주", "광주광역시"), // "광주시" 는 경기도 광주시의 정식 이름이기도 해서 표에 두지 않는다 (아래 parse 에서 다음 토큰으로 판별)
            Map.entry("대전", "대전광역시"), Map.entry("대전시", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("울산시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"), Map.entry("세종시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"), Map.entry("강원도", "강원특별자치도"),
            Map.entry("충북", "충청북도"), Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"), Map.entry("전라북도", "전북특별자치도"), Map.entry("전남", "전라남도"),
            Map.entry("경북", "경상북도"), Map.entry("경남", "경상남도"),
            Map.entry("제주", "제주특별자치도"), Map.entry("제주도", "제주특별자치도"));

    /** 시/도를 읽지 못한 주소를 받을 때의 안내 — 화면에 그대로 보인다 */
    public static final String MISSING_SIDO = "주소를 시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396";

    /**
     * 저장·예약 전에 부른다. 시/도를 못 읽으면 VALIDATION.
     * 주소 검색 API(§13-4) 전에는 고객이 주소를 직접 쓰는데, "강남대로 396" 처럼 시·도 없이 쓰면 매칭 후보가 0명이라
     * 예약이 매니저 누구에게도 가지 않고 '매니저를 찾고 있어요' 로 남았다 (직접 사용 점검 U1). 시군구는 없어도 된다(시도만 비교, OfferService.inArea)
     * 부르는 곳 예: ProfileService.addAddress, BookingService(예약 생성). VALIDATION 은 HTTP 400 이고 MISSING_SIDO 문구가 응답에 실린다
     */
    public static AddressRegion requireMatchable(String roadAddress) {
        AddressRegion region = parse(roadAddress);
        if (region.sido() == null) throw ApiException.validation(MISSING_SIDO);
        return region;
    }

    /**
     * 도로명주소 → (시·도, 시·군·구). 못 읽은 쪽은 null 이고 예외는 던지지 않는다 — 막아야 하면 requireMatchable 을 쓴다.
     * 순서: ① 시·도를 찾아 떼어 낸다 ② 남은 문자열의 첫 단어에서 시·군·구를 찾는다
     */
    public static AddressRegion parse(String roadAddress) {
        if (roadAddress == null) return new AddressRegion(null, null);
        String rest = roadAddress.trim(); // 시·도를 찾으면 그 뒤만 남긴다
        String sido = null;
        Matcher m = SIDO.matcher(rest);
        // find() = 정규식이 맞는 부분을 찾으면 true. group(1) = 괄호로 묶은 부분(시·도 단어), end() = 맞은 부분 바로 뒤 위치
        if (m.find()) {
            sido = canonicalSido(m.group(1));
            rest = rest.substring(m.end());
        } else {
            // 정식 접미사가 없는 첫 단어('서울'·'경기' 등) → 축약형 표(CANONICAL)에서 찾아본다
            int space = rest.indexOf(' ');
            String first = space < 0 ? rest : rest.substring(0, space);
            if (CANONICAL.containsKey(first)) {
                sido = CANONICAL.get(first);
                rest = space < 0 ? "" : rest.substring(space + 1).trim();
            } else if (first.equals("광주시")) {
                // 시/도 없이 '광주시 …': 다음이 '…구' 면 광주광역시(동구·서구·남구·북구·광산구), 읍·면·동이면 경기도 광주시
                String next = space < 0 ? "" : rest.substring(space + 1).trim();
                int nextSpace = next.indexOf(' ');
                String second = nextSpace < 0 ? next : next.substring(0, nextSpace);
                if (second.endsWith("구")) {
                    sido = "광주광역시";
                    rest = next;
                } else if (second.endsWith("읍") || second.endsWith("면") || second.endsWith("동")) {
                    sido = "경기도";
                    // rest 는 '광주시 …' 그대로 → sigungu = 광주시
                }
                // 둘 다 아니면 sido=null — 예약·주소 저장은 requireMatchable 이 VALIDATION 으로 막는다 (U1 전에는 후보 0명 로그로 운영자가 확인했다, §13-8)
            }
        }
        String sigungu = null;
        Matcher g = SIGUNGU.matcher(rest);
        if (g.find()) sigungu = g.group(1);
        return new AddressRegion(sido, sigungu);
    }

    /** 매니저 활동 지역(운영자 입력)도 같은 이름으로 맞춰 비교한다 */
    public static String canonicalSido(String sido) {
        if (sido == null) return null;
        String t = sido.trim();
        return CANONICAL.getOrDefault(t, t); // 표에 없으면(이미 정식 이름) 입력 그대로
    }
}
