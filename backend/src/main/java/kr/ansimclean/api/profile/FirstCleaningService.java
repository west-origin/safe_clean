package kr.ansimclean.api.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ansimclean.api.catalog.AddressSearchService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 1회 청소(가정/원룸 1회) 혜택은 휴대폰 번호 하나·주소 하나에 한 번. 다른 계정으로 로그인해도 같은 번호나 같은 집이면 막는다.
 * 취소된 예약의 혜택은 돌려준다(사용자 결정 2026-11-11) — 취소된 예약에 붙은 기록은 없는 것으로 본다.
 * 요금표 전환(2026-11-13)부터 체험은 가정(house)만이다 — 원룸 1회(home_type = studio)는 언제나 예약할 수 있어 세지 않는다.
 */
@Service
@Transactional
public class FirstCleaningService {
    public static final String ADDRESS_USED = "이 주소는 이미 첫 1회 청소 혜택을 이용했어요";
    private final JdbcClient jdbc;
    private final AddressSearchService addresses;
    public FirstCleaningService(JdbcClient jdbc, AddressSearchService addresses) { this.jdbc = jdbc; this.addresses = addresses; }
    public record Eligibility(boolean verified, boolean eligible, String reason, boolean completedOneTime) {}

    @Transactional(readOnly = true)
    public Eligibility eligibility(UUID id) {
        boolean completed = jdbc.sql("""
            select exists(select 1 from bookings where customer_id=:id
              and category_key in ('home','store') and frequency='oneTime' and status='done'
              and home_type is distinct from 'studio')
            """).param("id", id).query(Boolean.class).single();
        if (completed) return new Eligibility(false, false, "already_used", true);
        var phone = jdbc.sql("select phone from customer_phone_proofs where customer_id=:id").param("id", id).query(String.class).optional();
        if (phone.isEmpty()) return new Eligibility(false, false, "phone_required", false);
        boolean used = jdbc.sql("""
            select exists(select 1 from first_cleaning_claims c left join bookings b on b.id=c.booking_id
              where (c.phone=:phone or c.customer_id=:id) and b.status is distinct from 'cancelled')
            """).param("phone", phone.get()).param("id", id).query(Boolean.class).single();
        return new Eligibility(true, !used, used ? "already_used" : "eligible", false);
    }

    /** 예약 트랜잭션 안에서 부른다(예약 행은 이미 flush 됨). 번호·주소 중 하나라도 쓰였으면 INVALID_STATE 로 예약 전체가 되돌려진다 */
    public void claim(UUID id, UUID bookingId, String road, String detail) {
        requireUnusedAddress(bookingId, road, detail);
        jdbc.sql("""
            delete from first_cleaning_claims c using bookings b
            where b.id=c.booking_id and b.status='cancelled'
              and (c.customer_id=:id or c.phone=(select phone from customer_phone_proofs where customer_id=:id))
            """).param("id", id).update();
        // 번호가 PK 라 동시에 같은 번호로 들어와도 한 건만 들어간다. 다른 계정 정보를 드러내지 않게 ON CONFLICT 로 조용히 0건
        int count = jdbc.sql("""
            insert into first_cleaning_claims(phone, customer_id, booking_id, source)
            select phone, customer_id, :booking, 'booking' from customer_phone_proofs
            where customer_id=:id on conflict do nothing
            """).param("booking", bookingId).param("id", id).update();
        if (count != 1) throw new ApiException(ErrorCode.INVALID_STATE, "휴대폰 인증이 필요하거나 첫 1회 청소 혜택을 이미 이용했어요");
    }

    /**
     * 같은 집의 취소 안 된 1회 가정 청소가 (어느 계정이든) 있으면 막는다. 같은 건물 잠금으로 동시 예약도 한 건만.
     * 건물은 카카오 필지 고유값(building_key)으로 비교 — 지번으로 적어도 같은 건물. 고유값이 없는 쪽(키 없는 환경·옛 예약)은 도로명 글자로 비교
     */
    private void requireUnusedAddress(UUID bookingId, String road, String detail) {
        String building = addresses.buildingKey(road); // 카카오가 못 찾거나 장애면 여기서 예약 거절
        String sido = AddressRegion.parse(road).sido();
        Road key = road(road);
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:k))").param("k", building != null ? building : sido + "|" + key.key()).query().listOfRows();
        jdbc.sql("update bookings set building_key=:k where id=:booking").param("k", building).param("booking", bookingId).update();
        if (building != null) fillOldBookings(bookingId, road);
        List<Address> others = jdbc.sql("""
            select road_address, detail_address, building_key from bookings
            where category_key='home' and frequency='oneTime' and status<>'cancelled' and id<>:booking
              and home_type is distinct from 'studio'
              and (building_key=:building or (sido=:sido and replace(road_address,' ','') like :name))
            """).param("booking", bookingId).param("building", building).param("sido", sido).param("name", "%" + key.name() + "%")
            .query((rs, i) -> new Address(rs.getString(1), rs.getString(2), rs.getString(3))).list();
        for (Address o : others) {
            boolean sameBuilding = building != null && o.building() != null ? building.equals(o.building()) : sameRoad(road, o.road());
            if (sameBuilding && sameUnit(detail, o.detail())) throw new ApiException(ErrorCode.INVALID_STATE, ADDRESS_USED);
        }
    }

    /** 필지값이 없는 옛 예약(V063 전) 중 같은 시·군·구 것은 지금 카카오로 찾아 채운다 — 지번으로 적어 옛 예약을 비켜 가지 못하게. 예약이 성공하면 채운 값이 남아 다음엔 부르지 않는다(거절되면 함께 되돌려진다) */
    private void fillOldBookings(UUID bookingId, String road) {
        AddressRegion region = AddressRegion.parse(road);
        var old = jdbc.sql("""
            select id, road_address from bookings
            where building_key is null and category_key='home' and frequency='oneTime' and status<>'cancelled' and id<>:booking
              and sido=:sido and (cast(:sigungu as text) is null or sigungu is null or sigungu=:sigungu)
            """).param("booking", bookingId).param("sido", region.sido()).param("sigungu", region.sigungu())
            .query().listOfRows();
        for (var o : old) {
            try {
                String key = addresses.buildingKey((String) o.get("road_address"));
                jdbc.sql("update bookings set building_key=:k where id=:id").param("k", key).param("id", o.get("id")).update();
            } catch (ApiException ignored) { /* 카카오가 못 찾는 옛 주소는 도로명 글자 비교로 남는다 */ }
        }
    }

    private record Address(String road, String detail, String building) {}
    /** name = 도로명(공백 없이, 예: 봉은사로2길), key = 도로명 + 건물번호(앞 0 제거, 예: 봉은사로2길10, 강남대로396-1) */
    record Road(String name, String key) {}

    // 공백을 지운 "도로명(…로/…길, 봉은사로2길·중앙로123번길 포함) + 건물번호(본번-부번)"
    private static final Pattern ROAD = Pattern.compile("^(.+?(?:로|길)(?:\\d+번?[가-힣]?길)?)(\\d+)(?:-(\\d+))?");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /**
     * 같은 집인가. 도로명주소는 시·도 별칭("서울"="서울특별시")·띄어쓰기·괄호(동 이름)·구 생략을 무시하고 도로명+건물번호로 비교.
     * 상세주소는 숫자만 본다 — "103호"="103 호"="103오"="103", "101동 1001호"="101-1001". 한쪽 숫자가 다른 쪽에 모두 들어 있으면 같은 집
     * ("1001호" ⊂ "101동 1001호", "301호" ⊂ "3층 301호", 상세가 비면 건물 전체와 같다). "101동 1001호" ≠ "102동 1001호"
     */
    static boolean sameHome(String road1, String detail1, String road2, String detail2) {
        return sameRoad(road1, road2) && sameUnit(detail1, detail2);
    }

    static boolean sameRoad(String road1, String road2) {
        AddressRegion a = AddressRegion.parse(road1), b = AddressRegion.parse(road2);
        if (!Objects.equals(a.sido(), b.sido()) || !road(road1).key().equals(road(road2).key())) return false;
        return a.sigungu() == null || b.sigungu() == null || a.sigungu().equals(b.sigungu()); // 구를 빼고 쓴 주소는 같은 시·도 안에서 비교
    }

    static boolean sameUnit(String detail1, String detail2) {
        List<String> x = numbers(detail1), y = numbers(detail2);
        List<String> big = new ArrayList<>(x.size() >= y.size() ? x : y);
        for (String n : x.size() >= y.size() ? y : x) if (!big.remove(n)) return false;
        return true;
    }

    static Road road(String road) {
        List<String> tokens = new ArrayList<>(List.of(road.replaceAll("\\([^)]*\\)", " ").trim().split("\\s+")));
        if (AddressRegion.parse(road).sido() != null) tokens.remove(0); // "서울"·"서울특별시" 등
        while (tokens.size() > 1 && tokens.get(0).matches(".+[시군구읍면동리]")) tokens.remove(0); // 강남구·수원시 영통구·봉담읍 — 빼고 써도 같게
        String rest = String.join("", tokens).replace(",", ""); // "강남 대로 396" = "강남대로396"
        Matcher m = ROAD.matcher(rest);
        if (!m.find()) return new Road(rest, rest); // 도로명주소가 아니면 글자 그대로 비교
        String no = trimZeros(m.group(2)) + (m.group(3) == null ? "" : "-" + trimZeros(m.group(3)));
        return new Road(m.group(1), m.group(1) + no);
    }

    static List<String> numbers(String detail) {
        List<String> out = new ArrayList<>();
        if (detail == null) return out;
        Matcher m = NUMBER.matcher(detail);
        while (m.find()) out.add(trimZeros(m.group()));
        return out;
    }

    private static String trimZeros(String digits) { return digits.replaceFirst("^0+(?=\\d)", ""); }
}
