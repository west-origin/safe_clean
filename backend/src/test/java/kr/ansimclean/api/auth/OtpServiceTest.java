package kr.ansimclean.api.auth;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.sms.SmsSender;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 준비물(파일 아래쪽 '가짜 협력자'): 메모리 목록에 저장하는 FakeRepository, 보낸 문자를 기록하는 RecordingSms,
// advance 로 시간을 미는 MutableClock(이 파일 전용). TransactionTemplate 은 가짜 트랜잭션 관리자라 콜백을 그냥 실행한다.
// 인증번호 원문은 DB 에 없으므로 테스트는 가짜 문자 본문에서 6자리를 읽어 쓴다.
/** 인증번호 발급·검증·소비 규칙 (BACKEND_PLAN.md §7.1). 저장소와 시계는 가짜로 두고 규칙만 본다. */
class OtpServiceTest {
    static final String PHONE = "+821012345678";
    static final Pattern CODE_IN_TEXT = Pattern.compile("인증번호 (\\d{6})"); // 문자 본문에서 6자리 숫자를 꺼내는 정규식

    FakeRepository repository;
    RecordingSms sms;
    MutableClock clock;
    OtpService service;

    // 매 테스트 전(@BeforeEach) 가짜들을 새로 만들어 테스트끼리 기록이 섞이지 않게 한다
    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        sms = new RecordingSms();
        clock = new MutableClock(Instant.parse("2026-09-15T05:00:00Z")); // 서울 14:00
        service = new OtpService(repository, sms, new AppClock(clock), new TransactionTemplate(mock(PlatformTransactionManager.class)), "test-salt");
    }

    /** 고객으로 인증번호를 요청하고, 가짜 문자에 적힌 6자리를 꺼내 돌려준다 */
    String requestAndReadCode() {
        service.request(PHONE, "customer");
        var matcher = CODE_IN_TEXT.matcher(sms.lastText);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    @Test
    void 빈_솔트로는_기동하지_않는다() {
        // 솔트(해시에 섞는 서버 비밀 문자열)가 공백뿐이면 생성자에서 IllegalStateException — 서버가 뜨지 않는다
        assertThatThrownBy(() -> new OtpService(repository, sms, new AppClock(clock), new TransactionTemplate(mock(PlatformTransactionManager.class)), " "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OTP_SALT");
    }

    @Test
    void 이미_확인한_인증번호로_다시_verify_할_수_없다() {
        String code = requestAndReadCode();
        String token = service.verify(PHONE, "customer", code);
        assertThatThrownBy(() -> service.verify(PHONE, "customer", code))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED);
        assertThat(service.peek(token).getToken()).isEqualTo(token); // 먼저 받은 토큰은 그대로 유효
    }

    @Test
    void 발송_문자에_6자리_인증번호가_들어가고_원문은_저장하지_않는다() {
        String code = requestAndReadCode();
        assertThat(sms.lastPhone).isEqualTo(PHONE);
        assertThat(sms.lastText).contains("[안심클린]").contains("5분");
        PhoneVerification saved = repository.rows.get(0);
        assertThat(saved.getCodeHash()).isNotEqualTo(code).hasSize(64); // SHA-256 hex
        assertThat(saved.getExpiresAt()).isEqualTo(clock.now.plus(OtpService.CODE_TTL));
    }

    @Test
    void 맞는_번호면_가입에_쓸_토큰을_주고_한_번만_쓸_수_있다() {
        String code = requestAndReadCode();
        String token = service.verify(PHONE, "customer", code);
        assertThat(token).isNotBlank();

        PhoneVerification consumed = service.consume(token, "customer");
        assertThat(consumed.getPhone()).isEqualTo(PHONE);
        assertThatThrownBy(() -> service.consume(token, "customer"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void 다른_역할의_토큰은_쓸_수_없다() {
        String token = service.verify(PHONE, "customer", requestAndReadCode());
        assertThatThrownBy(() -> service.consume(token, "manager")).isInstanceOf(ApiException.class);
    }

    @Test
    void 토큰은_15분이_지나면_못_쓴다() {
        String token = service.verify(PHONE, "customer", requestAndReadCode());
        // 가짜 시계를 15분 1초 미래로 민다
        clock.advance(OtpService.TOKEN_TTL.plusSeconds(1));
        assertThatThrownBy(() -> service.consume(token, "customer"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void 다섯_번_틀리면_맞는_번호도_받지_않는다() {
        // "000000" 으로 한도(MAX_ATTEMPTS=5)만큼 틀린 뒤 → 맞는 번호를 넣어도 '너무 여러 번' 오류
        // (받은 번호가 우연히 000000 이면(백만 분의 1) 첫 시도가 맞아 버려 이 테스트는 실패한다)
        String code = requestAndReadCode();
        for (int i = 0; i < PhoneVerification.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verify(PHONE, "customer", "000000"))
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_INVALID);
        }
        assertThatThrownBy(() -> service.verify(PHONE, "customer", code))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("너무 여러 번");
    }

    @Test
    void 인증번호는_5분이_지나면_만료() {
        String code = requestAndReadCode();
        clock.advance(OtpService.CODE_TTL.plusSeconds(1));
        assertThatThrownBy(() -> service.verify(PHONE, "customer", code))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    @Test
    void 같은_번호로_60초_안에_다시_보내지_않고_시간당_5회까지만() {
        // 첫 발송 직후 다시 → 60초 제한. 그 뒤 61초씩 넘기며 모두 5회를 보낸 다음 1건 더 → 시간당 한도. 실제 발송 수는 5
        service.request(PHONE, "customer");
        assertThatThrownBy(() -> service.request(PHONE, "customer"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.OTP_RATE_LIMITED);

        for (int i = 1; i < OtpService.MAX_PER_HOUR; i++) {
            clock.advance(Duration.ofSeconds(61));
            service.request(PHONE, "customer");
        }
        clock.advance(Duration.ofSeconds(61));
        assertThatThrownBy(() -> service.request(PHONE, "customer"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("너무 많아요");
        assertThat(sms.count).isEqualTo(OtpService.MAX_PER_HOUR);
    }

    @Test
    void 문자_발송이_실패하면_UPSTREAM() {
        sms.fail = true;
        assertThatThrownBy(() -> service.request(PHONE, "customer"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.UPSTREAM);
        assertThat(repository.rows).isEmpty(); // 기록이 남으면 60초 재요청이 막힌다
    }

    @Test
    void 번호_형식이_틀리면_VALIDATION() {
        // 앱이 보내는 010 뒤 8자리 → +8210… 국제 형식. 자릿수가 모자라면 입력 오류
        assertThat(OtpService.toE164("12345678")).isEqualTo(PHONE);
        assertThatThrownBy(() -> OtpService.toE164("123"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void 먼저_읽었어도_조건부_UPDATE_에서_지면_OTP_EXPIRED() {
        // 이 테스트만 Mockito mock 저장소를 쓴다: findByToken 은 '확인됨·미사용' 건을 주고,
        // consumeIfUnused 는 따로 정하지 않아 mock 기본값 0(바뀐 행 없음)을 돌려준다
        PhoneVerificationRepository repo = mock(PhoneVerificationRepository.class);
        PhoneVerification v = PhoneVerification.issue(PHONE, "customer", "hash", clock.now, clock.now.plusSeconds(300));
        v.succeed("raced-token", clock.now);
        org.mockito.Mockito.when(repo.findByToken("raced-token")).thenReturn(Optional.of(v));
        // 다른 트랜잭션이 먼저 소비했다 — 읽은 값은 '미사용' 이지만 UPDATE 영향 행은 0
        OtpService candidate = new OtpService(repo, sms, new AppClock(clock),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), "test-salt");
        assertThatThrownBy(() -> candidate.consume("raced-token", "customer"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    // ── 가짜 협력자 ─────────────────────────────────────────────
    /** 문자를 실제로 보내지 않고 마지막 번호·본문과 보낸 횟수만 기록한다. fail = true 면 발송 실패(SmsException)를 흉내 낸다 */
    static class RecordingSms implements SmsSender {
        String lastPhone;
        String lastText;
        int count;
        boolean fail;

        @Override
        public void send(String e164, String text) {
            if (fail) throw new SmsException("실패", null);
            lastPhone = e164;
            lastText = text;
            count++;
        }
    }

    /**
     * 이 파일 전용 시계 (공용 kr.ansimclean.api.MutableClock 과 이름만 같은 별개 클래스). now 를 직접 읽을 수 있고 advance 로 앞으로 민다.
     * withZone 이 자기 자신을 돌려주므로 AppClock 이 서울 시간대로 감싸도 같은 객체의 now 를 읽는다
     */
    static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        /** 지금 시각을 d 만큼 미래로 옮긴다 (만료·재발송 간격 검사용) */
        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public java.time.ZoneId getZone() { return AppClock.ZONE; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** JpaRepository 의 메서드 중 서비스가 쓰는 것만 구현한 가짜 (나머지는 쓰지 않는다) */
    static class FakeRepository implements PhoneVerificationRepository {
        final List<PhoneVerification> rows = new ArrayList<>(); // DB 표 대신인 메모리 목록 (테스트가 직접 들여다본다)

        // PhoneVerification 에는 createdAt 을 읽는 getter 가 없다 — 만료 시각(발급 + 5분)이 가장 늦은 건을 '최신' 으로 고른다
        @Override
        public Optional<PhoneVerification> findFirstByPhoneAndRoleOrderByCreatedAtDesc(String phone, String role) {
            return rows.stream().filter(v -> v.getPhone().equals(phone) && v.getRole().equals(role))
                    .max(Comparator.comparing(PhoneVerification::getExpiresAt));
        }

        @Override
        public Optional<PhoneVerification> findByToken(String token) {
            return rows.stream().filter(v -> token.equals(v.getToken())).findFirst();
        }

        // 진짜 저장소의 조건부 UPDATE 와 같은 조건(같은 역할·미사용·확인 시각이 oldest 보다 뒤). 바뀐 행 수 1 또는 0.
        // synchronized: 여러 스레드가 동시에 불러도 한 번에 하나씩만 들어와 하나만 1 을 받는다
        @Override
        public synchronized int consumeIfUnused(String token, String role, Instant now, Instant oldest) {
            PhoneVerification v = findByToken(token).orElse(null);
            if (v == null || !v.getRole().equals(role) || v.getConsumedAt() != null || v.getVerifiedAt() == null || !v.getVerifiedAt().isAfter(oldest)) return 0;
            v.consume(now);
            return 1;
        }

        // 발급 시각을 '만료 시각 − 5분' 으로 되돌려 after 보다 뒤에 발급된 건을 센다 (60초·시간당 한도 검사용)
        @Override
        public long countByPhoneAndCreatedAtAfter(String phone, Instant after) {
            return rows.stream().filter(v -> v.getPhone().equals(phone) && v.getExpiresAt().minus(OtpService.CODE_TTL).isAfter(after)).count();
        }

        // 오래된 기록 정리(AuthCleaner)용 — OtpService 는 부르지 않는다
        @Override
        public long deleteByCreatedAtBefore(Instant before) {
            return 0;
        }

        @Override
        public Object lockPhone(String phone) {
            return null; // 메모리 가짜에는 잠금이 없다
        }

        // 발급 기록 저장 = 목록에 추가. 이후 verify·succeed 가 바꾸는 값은 같은 객체라 따로 UPDATE 할 필요가 없다
        @Override
        public <S extends PhoneVerification> S save(S entity) {
            rows.add(entity);
            return entity;
        }

        // ── 아래는 이 테스트에서 쓰지 않는다 (예외: deleteById 는 문자 발송이 실패하면 OtpService.request 가 불러 기록을 지운다) ──
        @Override public List<PhoneVerification> findAll() { return rows; }
        @Override public List<PhoneVerification> findAll(org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<PhoneVerification> findAll(org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public List<PhoneVerification> findAllById(Iterable<java.util.UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public Optional<PhoneVerification> findById(java.util.UUID id) { return rows.stream().filter(v -> v.getId().equals(id)).findFirst(); }
        @Override public boolean existsById(java.util.UUID id) { return findById(id).isPresent(); }
        @Override public long count() { return rows.size(); }
        @Override public void deleteById(java.util.UUID id) { rows.removeIf(v -> v.getId().equals(id)); }
        @Override public void delete(PhoneVerification entity) { rows.remove(entity); }
        @Override public void deleteAllById(Iterable<? extends java.util.UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends PhoneVerification> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll() { rows.clear(); }
        @Override public void flush() {}
        @Override public <S extends PhoneVerification> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends PhoneVerification> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<PhoneVerification> entities) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<java.util.UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { rows.clear(); }
        @Override public PhoneVerification getOne(java.util.UUID id) { throw new UnsupportedOperationException(); }
        @Override public PhoneVerification getById(java.util.UUID id) { throw new UnsupportedOperationException(); }
        @Override public PhoneVerification getReferenceById(java.util.UUID id) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> List<S> findAll(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> long count(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification> boolean exists(org.springframework.data.domain.Example<S> example) { throw new UnsupportedOperationException(); }
        @Override public <S extends PhoneVerification, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
    }
}
