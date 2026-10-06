package kr.ansimclean.api.support;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.events.InquiryAnswered;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.support.dto.InquiryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 지원 (§5.5): 공지는 대상+all 최신순·다른 대상은 404, 문의는 공백 거부·2,000자 상한·멱등 */
// 단위 테스트(CODE_GUIDE.md 3-12): SupportService 를 new 로 만들고, 저장소·멱등·아웃박스·설정·프로필은 Mockito 가짜다.
// 시계는 NOW 로 고정한 AppClock, 속도 제한(RateLimiter)은 같은 시각에 고정한 시계를 받는 진짜 객체라 시간당 한도가 실제로 걸린다.
// 그 밖에 탈퇴 보관 기한(3년)·탈퇴와 겹친 문의·답변 푸시(아웃박스 이벤트)·고객센터 전화번호를 본다.
class SupportServiceTest {
    static final Instant NOW = Instant.parse("2026-09-15T05:00:00Z"); // 고정 시계의 '지금' (서울 14:00)
    static final UUID USER = UUID.randomUUID();
    // mock(클래스): 그 클래스의 가짜. when(…) 으로 정하지 않은 호출은 아무 일도 하지 않고 null·0·빈 값을 돌려준다
    FaqRepository faqs = mock(FaqRepository.class);
    NoticeRepository notices = mock(NoticeRepository.class);
    InquiryRepository inquiries = mock(InquiryRepository.class);
    Idempotent idempotent = mock(Idempotent.class);
    OutboxService outbox = mock(OutboxService.class);
    SupportService service;

    // 테스트용 공지 한 건 (publishedAt 은 UTC 문자열). new Notice() {} 의 {} 는 이름 없는 하위 클래스를 만드는 문법이다.
    // ReflectionTestUtils.setField(객체, "필드 이름", 값): private 필드에 이름으로 값을 직접 넣는다
    static Notice notice(String audience, String title, String publishedAt) {
        Notice n = new Notice() {}; // seed 전용 엔티티 — 생성 메서드가 없어 필드로 만든다
        ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(n, "audience", audience);
        ReflectionTestUtils.setField(n, "title", title);
        ReflectionTestUtils.setField(n, "body", "본문");
        ReflectionTestUtils.setField(n, "publishedAt", Instant.parse(publishedAt));
        return n;
    }

    kr.ansimclean.api.catalog.AppSettingsService settings; // app_settings 읽기 가짜 — 고객센터 전화번호 테스트가 돌려줄 값을 정한다
    // holdLive(탈퇴와 줄 서는 잠금) 가짜 — 정하지 않으면 null 을 돌려줘 submit 이 그냥 지나간다
    kr.ansimclean.api.profile.ProfileService profiles = org.mockito.Mockito.mock(kr.ansimclean.api.profile.ProfileService.class);

    // @BeforeEach: 각 테스트 전에 실행된다. 고정 시계로 서비스를 만들고, 멱등·저장 가짜의 기본 동작을 정한다
    @BeforeEach
    void setUp() {
        settings = org.mockito.Mockito.mock(kr.ansimclean.api.catalog.AppSettingsService.class);
        service = new SupportService(faqs, notices, inquiries, idempotent, outbox, new AppClock(Clock.fixed(NOW, AppClock.ZONE)), settings,
                new kr.ansimclean.api.common.web.RateLimiter(new AppClock(Clock.fixed(NOW, AppClock.ZONE))), profiles);
        // thenAnswer: 불릴 때마다 람다로 돌려줄 값을 만든다. inv.getArgument(n) = 번호 n 의 인자(번호는 0부터라 4 = 다섯째)
        // 멱등 가짜는 5번째 인자(실제 업무 람다)를 바로 실행한다 — 모든 요청이 '처음 온 요청' 처럼 처리된다
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        when(inquiries.save(any())).thenAnswer(inv -> inv.getArgument(0)); // 받은 엔티티를 그대로 돌려준다 — submit 이 그 id 로 응답을 만든다
    }

    // 준비: 저장소 가짜가 customer 대상 목록으로 [mine, all] 을 돌려주게 한다 (최신순 정렬은 NoticeRepository.findVisible 쿼리 몫).
    // 확인: 날짜가 서울 날짜 "2026.09.10" 형식, 다른 대상(manager) 공지 본문은 NOT_FOUND, audience 가 customer·manager 가 아니면 VALIDATION
    @Test
    void 공지_목록은_날짜를_점_형식으로_주고_다른_대상의_공지_본문은_404() {
        Notice mine = notice("customer", "추석 연휴 서비스 운영 안내", "2026-09-09T15:00:00Z"); // 09-10 00:00 KST
        Notice all = notice("all", "전체 공지", "2026-08-31T15:00:00Z"); // 09-01 00:00 KST — all 은 두 대상 모두에게 보인다
        Notice theirs = notice("manager", "정산 안내", "2026-09-09T15:00:00Z");
        when(notices.findVisible("customer")).thenReturn(List.of(mine, all));
        when(notices.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(notices.findById(theirs.getId())).thenReturn(Optional.of(theirs));

        // extracting(…): 목록 각 항목에서 그 필드들만 뽑아 묶음(tuple)으로 비교한다. containsExactly = 순서까지 같아야 통과
        assertThat(service.notices("customer")).extracting("title", "date").containsExactly(
                org.assertj.core.groups.Tuple.tuple("추석 연휴 서비스 운영 안내", "2026.09.10"), org.assertj.core.groups.Tuple.tuple("전체 공지", "2026.09.01"));
        assertThat(service.notice(mine.getId(), "customer").body()).isEqualTo("본문");
        // assertThatThrownBy(() -> …): 람다를 실행해 예외가 나는지 보고, extracting("code") 로 ApiException 의 오류 코드를 비교한다
        assertThatThrownBy(() -> service.notice(theirs.getId(), "customer")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> service.notices("admin")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    // 먼저 검증 실패 두 경우(공백·줄바꿈만, 2,001자)는 VALIDATION 이고 save 가 한 번도 불리지 않는다(never).
    // 다음 정상 문의는 save 에 넘어간 Inquiry 를 붙잡아 내용(앞뒤 공백 제거)·작성자·상태·시각을 보고,
    // 멱등 처리에 넘긴 키·사용자·요청 내용을 확인한다
    @Test
    void 문의는_공백만이면_거부하고_2000자를_넘으면_거부하고_앞뒤_공백은_잘라_저장한다() {
        assertThatThrownBy(() -> service.submit(USER, null, "   \n")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.submit(USER, null, "가".repeat(2001))).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        verify(inquiries, never()).save(any());

        InquiryResponse res = service.submit(USER, "key-1", "  예약이 안 돼요  ");
        // ArgumentCaptor: 가짜에 넘어간 인자를 붙잡아 두었다가 getValue() 로 꺼내 보는 도구
        ArgumentCaptor<Inquiry> saved = ArgumentCaptor.forClass(Inquiry.class);
        verify(inquiries).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("예약이 안 돼요");
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getStatus()).isEqualTo("open");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(res.id()).isEqualTo(saved.getValue().getId());
        // 요청 내용 "inquiry:" + 공백을 뗀 본문은 Idempotent 가 해시로 저장해, 같은 키 재시도가 같은 내용인지 비교하는 재료다
        verify(idempotent).run(org.mockito.ArgumentMatchers.eq("key-1"), org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.eq("inquiry:예약이 안 돼요"), any(), any());
    }

    // retainAfterDeletion 이 이 사용자 문의의 보관 기한으로 '지금(서울 날짜 기준) + 3년' 을 넘기는지,
    // purgeExpiredInquiries 가 지금 시각으로 지운 개수를 그대로 돌려주는지 본다 (실제 update·delete 쿼리는 SupportIT 가 본다)
    @Test
    void 탈퇴하면_문의를_3년_뒤_지우도록_적고_매일_지난_것을_지운다() { // 자체 점검 #31 (사용자 결정 2026-09-27)
        service.retainAfterDeletion(USER);
        verify(inquiries).retainUntil(USER, NOW.atZone(AppClock.ZONE).plusYears(3).toInstant());
        when(inquiries.deletePurgeable(NOW)).thenReturn(2);
        assertThat(service.purgeExpiredInquiries()).isEqualTo(2);
    }

    // 준비: holdLive(탈퇴와 줄 서는 잠금)가 UNAUTHENTICATED 를 던지게 한다 = 탈퇴가 먼저 커밋된 상황.
    // 확인: submit 도 같은 오류(401)로 끝나고 save 는 불리지 않는다
    @Test
    void 탈퇴가_먼저_커밋됐으면_문의는_401_이고_남지_않는다() { // 자체 점검 #31 — 탈퇴 뒤에 커밋된 문의는 보관 기한이 안 적혀 영영 남았다
        when(profiles.holdLive(USER)).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.submit(USER, null, "문의")).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.UNAUTHENTICATED);
        verify(inquiries, org.mockito.Mockito.never()).save(any());
    }

    // 준비: open 상태 문의 하나를 저장소 가짜가 id 로 돌려주게 한다.
    // 확인: answer 가 앞뒤 공백을 뗀 답변·answered 상태·답변 시각 표시를 돌려주고, 아웃박스에 InquiryAnswered(문의 id, 작성자)를
    // 남긴다(푸시는 나중에 아웃박스가 보낸다). 이어서 내 문의 목록에도 그 답변이 보인다
    @Test
    void 답변하면_상태가_answered_가_되고_사용자에게_푸시_이벤트가_간다_내_문의_목록에_답변이_보인다() {
        UUID user = UUID.randomUUID();
        Inquiry i = Inquiry.open(user, "정기청소 일정을 바꾸고 싶어요", NOW);
        when(inquiries.findById(i.getId())).thenReturn(Optional.of(i));
        var answered = service.answer(i.getId(), " 내 예약 탭에서 '예약 변경' 을 눌러주세요 ");
        assertThat(answered.status()).isEqualTo("answered");
        assertThat(answered.answer()).isEqualTo("내 예약 탭에서 '예약 변경' 을 눌러주세요");
        assertThat(answered.answeredLabel()).isNotNull();
        verify(outbox).publish(new InquiryAnswered(i.getId(), user)); // record 는 필드 값이 같으면 equals 가 참이라 새로 만든 이벤트와 비교할 수 있다

        when(inquiries.findByUserIdOrderByCreatedAtDesc(user)).thenReturn(List.of(i));
        assertThat(service.myInquiries(user)).extracting("status", "answer").containsExactly(tuple("answered", "내 예약 탭에서 '예약 변경' 을 눌러주세요"));
    }

    // RateLimiter 는 진짜 객체이고 시계가 고정이라 모든 요청이 같은 1시간 창에 든다. 속도 제한 검사는 멱등 람다 안에 있는데,
    // 가짜 멱등이 람다를 늘 실행하므로 요청마다 센다 → 한도(INQUIRY_PER_USER_PER_HOUR)만큼은 통과하고 그다음은 RATE_LIMITED
    @Test
    void 같은_사용자의_문의는_시간당_10건까지() { // 감사 M-05
        when(inquiries.save(any())).thenAnswer(inv -> inv.getArgument(0));
        for (int i = 0; i < kr.ansimclean.api.common.web.RateLimiter.INQUIRY_PER_USER_PER_HOUR; i++) service.submit(USER, "k" + i, "문의 " + i);
        assertThatThrownBy(() -> service.submit(USER, "k-over", "하나 더"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(kr.ansimclean.api.common.error.ErrorCode.RATE_LIMITED);
    }

    // app_settings 의 support_phone 이 빈 문자열(V020 이 넣은 기본값)이면 null, 값이 있으면 그대로 돌려준다
    @Test
    void 고객센터_전화번호는_비어_있으면_null_이다() {
        when(settings.get("support_phone", String.class)).thenReturn("");
        org.assertj.core.api.Assertions.assertThat(service.supportPhone()).isNull(); // 앱이 전화 버튼을 그리지 않는다

        when(settings.get("support_phone", String.class)).thenReturn("1588-0000");
        org.assertj.core.api.Assertions.assertThat(service.supportPhone()).isEqualTo("1588-0000");
    }
}
