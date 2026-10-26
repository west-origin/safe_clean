package kr.ansimclean.api.booking;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.MutableClock;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.offer.Offer;
import kr.ansimclean.api.offer.OfferRepository;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.schedule.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 (Docker 필요): 청소 시작 → '전' 사진 2곳 → 한 곳만 '후' 사진(다시 찍기 포함) → 고객은 완료 전 ready=false →
 * 완료 → 고객은 전·후가 다 있는 한 곳만 본다 → 완료 뒤 매니저 쓰기는 409. 남의 예약은 404.
 * V055 마이그레이션과 엔티티가 맞는지(ddl-auto=validate)도 이 컨텍스트가 뜨면서 확인된다.
 * Storage 는 목이다(밖으로 나가지 않게) — 서명 URL 은 'signed:<경로>' 로 돌려준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(CleaningPhotoIT.FixedClock.class)
class CleaningPhotoIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-09-15T05:00:00Z"), AppClock.ZONE);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        AppClock testClock() {
            return new AppClock(CLOCK);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles;
    @Autowired
    AvailabilityService availability;
    @Autowired
    OfferRepository offers;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean
    SupabaseStorageClient storage;
    @MockitoBean
    JwtDecoder jwtDecoder;

    final JsonMapper json = JsonMapper.builder().build();
    ItSupport.User customer;
    ItSupport.User manager;

    @BeforeEach
    void setUp() {
        ItSupport.resetUserData(jdbc);
        CLOCK.set(Instant.parse("2026-09-15T05:00:00Z"));
        customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");
        manager = ItSupport.approvedManager(profiles, availability, jwtDecoder, "김미영", "서울특별시 강남구", "서울특별시");
        when(storage.signedUrl(eq(CleaningPhotoService.BUCKET), anyString(), anyInt())).thenAnswer(i -> "signed:" + i.getArgument(1));
    }

    /** 예약을 만들고 매니저가 수락한 뒤 당일 10:00(서울)에 청소를 시작한다. 돌려주는 값 = 예약 id */
    UUID started() throws Exception {
        String res = mvc.perform(post("/api/v1/customer/bookings").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(ItSupport.bookingBody("2026-09-16", "10:00"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID id = UUID.fromString(json.readTree(res).get("id").asString());
        Offer offer = offers.findByBookingId(id).stream().filter(Offer::isPending).findFirst().orElseThrow();
        mvc.perform(post("/api/v1/manager/offers/" + offer.getId() + "/accept").header("Authorization", manager.bearer())).andExpect(status().isOk());
        CLOCK.set(Instant.parse("2026-09-16T01:00:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + id + "/start").header("Authorization", manager.bearer())).andExpect(status().isOk());
        return id;
    }

    static MockMultipartFile png() {
        return new MockMultipartFile("file", "a.png", "image/png", RepairPhotoServiceTest.png(10, 10));
    }

    JsonNode read(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void 전후_사진_흐름() throws Exception {
        UUID job = started();
        String base = "/api/v1/manager/jobs/" + job + "/photos";

        JsonNode living = read(mvc.perform(multipart(base).file(png()).param("location", " 거실 소파 앞 ").header("Authorization", manager.bearer())).andExpect(status().isOk()));
        assertThat(living.get("location").asString()).isEqualTo("거실 소파 앞");
        assertThat(living.get("afterUrl").isNull()).isTrue();
        String livingId = living.get("id").asString();
        assertThat(living.get("beforeUrl").asString()).isEqualTo("signed:" + job + "/" + livingId + "-before.png");
        mvc.perform(multipart(base).file(png()).param("location", "주방").header("Authorization", manager.bearer())).andExpect(status().isOk());

        // 고객: 아직 끝나지 않았다
        mvc.perform(get("/api/v1/customer/bookings/" + job + "/photos").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false)).andExpect(jsonPath("$.photos").isEmpty());

        // '후' 사진 — 두 번 찍으면 바뀌고 예전 파일은 지워진다
        String first = read(mvc.perform(multipart(HttpMethod.PUT, base + "/" + livingId + "/after").file(png()).header("Authorization", manager.bearer())).andExpect(status().isOk()))
                .get("afterUrl").asString().substring("signed:".length());
        String second = read(mvc.perform(multipart(HttpMethod.PUT, base + "/" + livingId + "/after").file(png()).header("Authorization", manager.bearer())).andExpect(status().isOk()))
                .get("afterUrl").asString().substring("signed:".length());
        assertThat(second).isNotEqualTo(first).startsWith(job + "/" + livingId + "-after-");
        verify(storage).delete(CleaningPhotoService.BUCKET, first);

        mvc.perform(get(base).header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.max").value(10))
                .andExpect(jsonPath("$.photos.length()").value(2)).andExpect(jsonPath("$.photos[0].location").value("거실 소파 앞"));

        // 완료 → 고객은 전·후가 다 있는 '거실 소파 앞' 만 본다
        CLOCK.set(Instant.parse("2026-09-16T03:00:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + job + "/complete").header("Authorization", manager.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/customer/bookings/" + job + "/photos").header("Authorization", customer.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].id").value(livingId))
                .andExpect(jsonPath("$.photos[0].afterUrl").value("signed:" + second));

        // 완료 뒤에는 찍기·지우기 409, 목록은 볼 수 있다
        mvc.perform(multipart(base).file(png()).param("location", "욕실").header("Authorization", manager.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
        mvc.perform(delete(base + "/" + livingId).header("Authorization", manager.bearer())).andExpect(status().isConflict());
        mvc.perform(get(base).header("Authorization", manager.bearer())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from cleaning_photos where booking_id = ?::uuid", Integer.class, job.toString())).isEqualTo(2);
    }

    @Test
    void 지우기와_남의_예약() throws Exception {
        UUID job = started();
        String base = "/api/v1/manager/jobs/" + job + "/photos";
        String id = read(mvc.perform(multipart(base).file(png()).param("location", "거실").header("Authorization", manager.bearer())).andExpect(status().isOk())).get("id").asString();
        mvc.perform(delete(base + "/" + id).header("Authorization", manager.bearer())).andExpect(status().isNoContent());
        mvc.perform(delete(base + "/" + id).header("Authorization", manager.bearer())).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from cleaning_photos", Integer.class)).isZero();

        // 다른 고객은 이 예약의 사진을 볼 수 없다
        ItSupport.User other = ItSupport.signedUp(profiles, jwtDecoder, "customer", "박하늘");
        mvc.perform(get("/api/v1/customer/bookings/" + job + "/photos").header("Authorization", other.bearer())).andExpect(status().isNotFound());
    }
}
