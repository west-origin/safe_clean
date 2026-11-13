package kr.ansimclean.api.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가정 정기청소 첫 결제 혜택 (사용자 결정 2026-11-14, docs/domain.md '첫 결제 혜택·정기 요금제 금액권', V075).
 * 가정 주 N회 요금제 한 달치 결제가 확정된(paid·partially_refunded) 고객이 다음에 만드는 분해청소(후드·덕트) 예약 1건을 추가 요금까지 전액 무료로.
 * 쿠폰이 아니라 예약을 만들 때 자동으로 붙는다(고객이 고르지 않는다). 고객당 1회 + 같은 집 1회 — 같은 집 = 원천 시리즈의 첫 회차 주소를
 * 첫 1회 체험과 같은 규칙(FirstCleaningService.sameRoad·sameUnit, 필지값이 둘 다 있으면 필지값)으로 비교한다.
 * 혜택을 쓴 예약이 취소되면 혜택은 돌아온다 — 취소된 예약에 붙은 기록은 없는 것으로 본다(첫 1회 체험과 같다).
 * 원천 시리즈 결제가 나중에 전부 환불돼도 이미 쓴 혜택은 그대로 둔다. 대리석 코팅·크랙 수리 15만 원 할인은 수리 결제가 앱에 없어 아직 안내만.
 */
@Service
@Transactional
public class FirstPaymentBenefitService {
    private final JdbcClient jdbc;

    public FirstPaymentBenefitService(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** 혜택의 근거가 되는 가정 정기 시리즈와 그 집 주소 (첫 회차 기준) */
    record Source(UUID seriesId, String road, String detail, String building) {}

    /** 지금 분해청소 예약을 만들면 혜택이 붙나 (견적·앱 안내). 잠그지 않는다 — 생성(claim)이 잠근 뒤 다시 판정한다 */
    @Transactional(readOnly = true)
    public boolean available(UUID customerId) {
        return source(customerId, false).isPresent();
    }

    /**
     * 예약 트랜잭션 안에서 부른다(예약 행은 이미 flush 됨). 혜택이 붙으면 기록을 남기고 할인 금액(= amount, 전액)을, 아니면 0.
     * 고객 잠금 → 집 잠금 순서로, 동시에 두 예약을 만들어도 혜택은 하나만 붙는다
     */
    public long claim(UUID customerId, UUID bookingId, long amount) {
        if (amount <= 0) return 0;
        lock("first-payment:" + customerId);
        Optional<Source> s = source(customerId, true);
        if (s.isEmpty()) return 0;
        jdbc.sql("""
            insert into first_payment_benefits(customer_id, booking_id, source_series_id, road_address, detail_address, building_key, amount)
            values (:c, :b, :s, :road, :detail, :building, :a)
            """).param("c", customerId).param("b", bookingId).param("s", s.get().seriesId()).param("road", s.get().road())
            .param("detail", s.get().detail()).param("building", s.get().building()).param("a", amount).update();
        return amount;
    }

    /** 이 고객이 아직 혜택을 안 썼으면, 결제가 확정된 가정 정기 시리즈 중 그 집이 혜택을 안 쓴 첫 시리즈 (결제 순) */
    private Optional<Source> source(UUID customerId, boolean lock) {
        boolean used = jdbc.sql("""
            select exists(select 1 from first_payment_benefits f join bookings b on b.id = f.booking_id
              where f.customer_id = :c and b.status <> 'cancelled')
            """).param("c", customerId).query(Boolean.class).single();
        if (used) return Optional.empty();
        List<Source> candidates = jdbc.sql("""
            select s.id, v.road_address, v.detail_address, v.building_key from booking_series s
            join lateral (select min(p.created_at) paid_at from payments p
                          where p.series_id = s.id and p.status in ('paid', 'partially_refunded')) p on p.paid_at is not null
            join lateral (select road_address, detail_address, building_key from bookings
                          where series_id = s.id order by service_date, id limit 1) v on true
            where s.customer_id = :c and s.price_plan like 'house\\_weekly\\_%'
            order by p.paid_at, s.id
            """).param("c", customerId)
            .query((rs, i) -> new Source(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4))).list();
        for (Source c : candidates) {
            if (lock) lock("first-payment-home:" + homeKey(c));
            if (!homeUsed(c)) return Optional.of(c);
        }
        return Optional.empty();
    }

    /** 같은 집의 혜택이 (어느 계정이든) 살아 있나. 건물은 필지값이 둘 다 있으면 필지값, 아니면 도로명 + 건물번호 글자로 비교 */
    private boolean homeUsed(Source c) {
        String sido = AddressRegion.parse(c.road()).sido();
        List<Source> others = jdbc.sql("""
            select f.source_series_id, f.road_address, f.detail_address, f.building_key from first_payment_benefits f
            join bookings b on b.id = f.booking_id
            where b.status <> 'cancelled' and (f.building_key = :building or replace(f.road_address, ' ', '') like :name)
            """).param("building", c.building()).param("name", "%" + FirstCleaningService.road(c.road()).name() + "%")
            .query((rs, i) -> new Source(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4))).list();
        for (Source o : others) {
            boolean sameBuilding = c.building() != null && o.building() != null ? c.building().equals(o.building())
                    : java.util.Objects.equals(sido, AddressRegion.parse(o.road()).sido()) && FirstCleaningService.sameRoad(c.road(), o.road());
            if (sameBuilding && FirstCleaningService.sameUnit(c.detail(), o.detail())) return true;
        }
        return false;
    }

    /** 잠금 키의 집 부분: 필지값, 없으면 시·도 + 도로명·건물번호 (같은 건물이면 같은 키 — 호수는 잠금 안에서 비교) */
    private static String homeKey(Source c) {
        return c.building() != null ? c.building() : AddressRegion.parse(c.road()).sido() + "|" + FirstCleaningService.road(c.road()).key();
    }

    private void lock(String key) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext(:k))").param("k", key).query().listOfRows();
    }
}
