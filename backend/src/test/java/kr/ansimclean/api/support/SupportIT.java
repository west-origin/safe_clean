package kr.ansimclean.api.support;

import kr.ansimclean.api.ItSupport;
import kr.ansimclean.api.profile.ProfileService;
import kr.ansimclean.api.push.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 통합 (Docker 필요, B6 완료 기준 SupportIT): seed FAQ·공지 읽기 · 문의 저장 + 같은 키 재요청은 1건 · 푸시 토큰 등록/갱신 */
// 통합 테스트(CODE_GUIDE.md 3-12). @AutoConfigureMockMvc 의 MockMvc 요청은 진짜 보안 필터·서비스·DB 를 거친다.
// @ServiceConnection 이 스프링의 DB 접속 설정을 Testcontainers 로 띄운 Postgres 16 으로 맞춘다.
// 서버가 뜰 때 Flyway 가 db/migration 을 모두 적용하므로 FAQ·공지는 마이그레이션이 넣고 뒤 번호가 고친 뒤의 seed 를 읽는다.
// @ActiveProfiles("test") = application-test.yml. @MockitoBean 으로 바꾸는 것은 JwtDecoder 하나뿐이고,
// 푸시·문자·LiveKit 은 그 프로필의 logging 모드(밖으로 보내지 않음)다.
// 이 클래스는 테스트 사이에 DB 를 비우지 않는다 (ItSupport.resetUserData 를 부르지 않음) — 개수는 전후 차이로 본다.
// 그 밖에 탈퇴한 사용자의 문의 보관 기한(purge_after) 적기·정리, 답변과 탈퇴가 겹쳐도 기한이 지워지지 않는지를 실제 쿼리로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SupportIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine"); // static: 이 클래스의 테스트가 컨테이너 하나를 같이 쓴다

    // @Autowired: 스프링이 만들어 둔 진짜 객체를 필드에 넣는다 (테스트는 생성자 대신 필드로 받는다, CODE_GUIDE.md 3-2)
    @Autowired
    MockMvc mvc;
    @Autowired
    ProfileService profiles; // 준비용 — ItSupport.signedUp 이 가입을 마친 사용자를 만들 때 쓴다
    @Autowired
    InquiryRepository inquiries; // 문의가 DB 에 실제로 몇 건·어떤 값으로 저장됐는지 직접 본다
    @Autowired
    SupportService supportService; // 탈퇴 보관·정리 메서드를 HTTP 없이 직접 부른다
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc; // SQL 을 직접 실행하는 도구 — 보관 기한을 과거로 당길 때 쓴다
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager; // 트랜잭션을 코드로 열고 닫을 때 쓴다 (답변·탈퇴가 겹치는 테스트)
    @Autowired
    PushTokenRepository tokens; // 푸시 토큰 행을 직접 본다
    // 진짜 토큰 해석기(Supabase 서명 검사) 대신 가짜 — ItSupport.signedUp 이 만든 "Bearer t-…" 토큰을 그 사용자로 풀게 정한다
    @MockitoBean
    JwtDecoder jwtDecoder;

    // 한 흐름으로 이어서 본다.
    // ① 로그인 없이 FAQ·공지 목록·공지 본문을 읽는다 (seed 값). 고객 공지를 audience=manager 로 읽으면 404
    // ② 같은 Idempotency-Key 로 문의를 두 번 보내도 inquiries 행은 1건만 늘어난다
    // ③ 같은 사용자가 같은 토큰을 platform 만 바꿔 다시 등록하면 행 하나가 갱신되고,
    //    다른 사용자가 같은 토큰을 등록하면 앞 사용자의 행이 지워진다 (PushService.register)
    @Test
    void FAQ_공지는_seed_를_읽고_문의는_저장되며_같은_키는_한_번만_푸시_토큰은_갱신된다() throws Exception {
        ItSupport.User customer = ItSupport.signedUp(profiles, jwtDecoder, "customer", "김하늘");

        // V004 가 고객 FAQ 4개를 sort 1~4 로 넣었다. 첫 항목은 sort 1 인 질문 (V027·V035·V049·V050 은 답변만 고쳐 질문·순서는 그대로다)
        mvc.perform(get("/api/v1/support/faq").param("audience", "customer")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(4))).andExpect(jsonPath("$[0].question").value("예약을 변경하거나 취소할 수 있나요?"))
                // V050 이 고친 '매니저는 언제 배정되나요?' 답 — 밤 보류(문제 목록 2번)를 말한다. 앱 가짜 서버 mock.js FAQ f3 와 같은 글자 (합친 38건 검토 R9)
                .andExpect(jsonPath("$[?(@.question == '매니저는 언제 배정되나요?')].answer").value(hasItem(
                        "1회 청소는 예약하면 바로 매니저를 찾기 시작해요. 밤 9시~아침 8시에 한 예약은 아침 8시부터 찾아요. 정기청소는 첫 방문은 바로, 이후 방문은 각 방문 7일 전부터 찾아요. 배정되면 알림으로 알려드려요.")));
        // 공지 목록은 게시 시각 최신순. V004 가 고객 공지 3개를 넣고 V029 가 가장 최근의 '추석 연휴 서비스 운영 안내'(2026.09.10)를 지웠다
        // (2026-09-24 사용자 결정 E2: 공휴일에도 예약을 받으므로 '연휴에는 방문하지 않아요' 공지와 어긋났다).
        // 그래서 첫 고객 공지는 V004 의 '실시간 청소 영상 기능이 추가되었어요'(2026.09.01)이고, 지운 공지는 목록에 없어야 한다
        String notices = mvc.perform(get("/api/v1/support/notices").param("audience", "customer")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("실시간 청소 영상 기능이 추가되었어요")).andExpect(jsonPath("$[0].date").value("2026.09.01"))
                .andExpect(jsonPath("$[*].title", not(hasItem("추석 연휴 서비스 운영 안내"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 응답 문자열을 JSON 트리로 읽어 첫 공지의 id 를 꺼낸다 (tools.jackson = Jackson 3 패키지)
        String firstId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(notices).get(0).get("id").asString();
        mvc.perform(get("/api/v1/support/notices/" + firstId).param("audience", "customer")).andExpect(status().isOk()).andExpect(jsonPath("$.body").isNotEmpty());
        mvc.perform(get("/api/v1/support/notices/" + firstId).param("audience", "manager")).andExpect(status().isNotFound()); // 고객 공지는 매니저에게 없다

        // 멱등 키는 무작위 UUID. DB 를 비우지 않으므로 전체 개수 대신 요청 전후의 차이를 본다
        String key = UUID.randomUUID().toString();
        long before = inquiries.count();
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization", customer.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"예약이 안 돼요\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.id").isNotEmpty());
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization", customer.bearer()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"예약이 안 돼요\"}")).andExpect(status().isOk());
        assertThat(inquiries.count()).isEqualTo(before + 1); // 두 번째 요청은 저장해 둔 응답을 돌려줄 뿐 행을 더 만들지 않는다 (Idempotent.run)
        // anyMatch: 목록 중 조건에 맞는 행이 하나라도 있으면 통과 — 이 고객의 글이 open 상태로 저장됐는지
        assertThat(inquiries.findAll()).anyMatch(i -> i.getUserId().equals(customer.id()) && i.getContent().equals("예약이 안 돼요") && i.getStatus().equals("open"));

        mvc.perform(put("/api/v1/push/token").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"ios\",\"token\":\"tok-1\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/push/token").header("Authorization", customer.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"android\",\"token\":\"tok-1\"}")).andExpect(status().isOk());
        // 같은 (사용자, 토큰) 행이 이미 있어 새 행 대신 platform 만 바뀐다 (PushService.register 의 touch)
        assertThat(tokens.findByUserId(customer.id())).hasSize(1).first().extracting("platform").isEqualTo("android");
        // 같은 기기에 다른 사용자가 로그인하면 앞 사람의 토큰은 사라진다
        ItSupport.User other = ItSupport.signedUp(profiles, jwtDecoder, "customer", "박지훈");
        mvc.perform(put("/api/v1/push/token").header("Authorization", other.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"android\",\"token\":\"tok-1\"}")).andExpect(status().isOk());
        assertThat(tokens.findByUserId(customer.id())).isEmpty();
        assertThat(tokens.findByUserId(other.id())).hasSize(1);
    }

    // 준비: 한 고객의 문의 2건을 저장하고, 실제 탈퇴(AuthService.deleteAccount) 대신 그 안에서 부르는 retainAfterDeletion 만
    // 직접 불러 두 건 모두 purge_after(탈퇴 + 3년)를 적는다. 그중 old 만 SQL 로 purge_after 를 어제로 당긴다.
    // 확인: purgeExpiredInquiries 가 old 1건만 지우고 kept 는 남긴다. DB 를 비우지 않지만 이 클래스의 다른 테스트는
    // purge_after 를 과거로 두지 않아(NULL 이거나 3년 뒤) 지운 수가 정확히 1 이다
    @Test
    void 탈퇴_뒤_보관_기간이_지난_문의만_지운다() { // 자체 점검 #31 — 실제 update·delete 쿼리
        ItSupport.User u = ItSupport.signedUp(profiles, jwtDecoder, "customer", "보관고객");
        Inquiry kept = inquiries.save(Inquiry.open(u.id(), "아직 보관", java.time.Instant.now()));
        Inquiry old = inquiries.save(Inquiry.open(u.id(), "기간 지남", java.time.Instant.now()));
        supportService.retainAfterDeletion(u.id());
        assertThat(inquiries.findById(kept.getId()).orElseThrow().getPurgeAfter()).isNotNull();
        jdbc.update("update inquiries set purge_after = now() - interval '1 day' where id = ?", old.getId()); // 3년을 기다리지 않고 '기간 지남' 을 만든다
        assertThat(supportService.purgeExpiredInquiries()).isEqualTo(1);
        assertThat(inquiries.findById(old.getId())).isEmpty();
        assertThat(inquiries.findById(kept.getId())).isPresent();
    }

    // 두 트랜잭션이 겹치는 순간을 코드로 만든다: 바깥 트랜잭션이 문의를 읽어 둔 사이(purge_after 는 NULL),
    // 안쪽 트랜잭션(REQUIRES_NEW 라 바깥에 합쳐지지 않고 먼저 커밋된다, CODE_GUIDE.md 3-3)이 탈퇴 보관 기한을 적는다.
    // retainAfterDeletion 은 update 쿼리(retainUntil)라 바깥이 읽어 둔 객체의 purge_after 는 NULL 그대로다.
    // 그 뒤 바깥이 답변을 적고 커밋해도 purge_after 가 남아 있어야 한다 — Inquiry 의 @DynamicUpdate 로 바뀐 열만 UPDATE 하기 때문
    @Test
    void 답변을_읽은_사이_탈퇴가_보관_기한을_적어도_답변_저장이_지우지_않는다() { // 자체 점검 후속 검토 — 전체 열 UPDATE 가 purge_after 를 NULL 로 되돌렸다
        ItSupport.User u = ItSupport.signedUp(profiles, jwtDecoder, "customer", "겹침고객");
        Inquiry q = inquiries.save(Inquiry.open(u.id(), "겹침", java.time.Instant.now()));
        // TransactionTemplate: 람다 안의 코드를 한 트랜잭션으로 실행하고, 람다가 끝나면 커밋한다
        var outer = new org.springframework.transaction.support.TransactionTemplate(txManager);
        var inner = new org.springframework.transaction.support.TransactionTemplate(txManager);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(s -> {
            Inquiry loaded = inquiries.findById(q.getId()).orElseThrow(); // purge_after 는 아직 NULL
            inner.executeWithoutResult(s2 -> supportService.retainAfterDeletion(u.id())); // 그 사이 탈퇴 트랜잭션이 커밋
            loaded.answer("답변드려요", java.time.Instant.now()); // 커밋 때 flush
        });
        Inquiry after = inquiries.findById(q.getId()).orElseThrow();
        assertThat(after.getPurgeAfter()).isNotNull();
        assertThat(after.getAnswer()).isEqualTo("답변드려요");
    }
}
