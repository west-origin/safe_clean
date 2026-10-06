package kr.ansimclean.api.support;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.support.dto.FaqResponse;
import kr.ansimclean.api.common.events.InquiryAnswered;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.support.dto.InquiryResponse;
import kr.ansimclean.api.support.dto.MyInquiryResponse;
import kr.ansimclean.api.support.dto.NoticeResponse;
import kr.ansimclean.api.support.dto.NoticeSummary;
import kr.ansimclean.api.common.web.RateLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 지원 (BACKEND_PLAN.md §5.5): FAQ·공지는 seed 를 읽기만, 문의는 저장(멱등). 약관은 TermsService */
// 클래스 전체 트랜잭션(CODE_GUIDE.md 3-3), 읽기 메서드만 readOnly 를 따로 붙였다. 부르는 곳 예: SupportController(앱),
// AdminInquiryController(운영자 답변). 아래쪽 탈퇴·보관 메서드(retainAfterDeletion·forgetInquiries)는 탈퇴·파기 쪽 묶음에
// 합쳐진다 — 여기서 실패하면 그 탈퇴(또는 그 계정의 파기)가 통째로 되돌려진다. 부르는 곳은 각 메서드 설명에 있다.
@Service
@Transactional
public class SupportService {
    private final FaqRepository faqs;
    private final NoticeRepository notices;
    private final InquiryRepository inquiries;
    private final Idempotent idempotent; // 멱등 키 처리 — 같은 문의 요청을 두 번 받아도 한 번만 저장
    private final AppClock clock;

    private final OutboxService outbox; // 답변 푸시를 DB 에 먼저 적어 두고 커밋 뒤에 보낸다 (아웃박스)
    private final AppSettingsService settings; // app_settings 표 — 고객센터 전화번호(support_phone)
    private final RateLimiter rateLimiter; // 사용자당 시간당 문의 수 제한
    private final kr.ansimclean.api.profile.ProfileService profiles; // 탈퇴와 줄을 세우는 잠금(holdLive)

    /** 탈퇴 뒤 1:1 문의 보관 기간 — 전자상거래법의 소비자 불만·분쟁처리 기록 (사용자 결정 2026-09-27, 자체 점검 #31) */
    static final int INQUIRY_RETENTION_YEARS = 3;

    public SupportService(FaqRepository faqs, NoticeRepository notices, InquiryRepository inquiries, Idempotent idempotent, OutboxService outbox, AppClock clock,
                          AppSettingsService settings, RateLimiter rateLimiter, kr.ansimclean.api.profile.ProfileService profiles) {
        this.profiles = profiles;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
        this.faqs = faqs;
        this.notices = notices;
        this.inquiries = inquiries;
        this.idempotent = idempotent;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * 고객센터 전화번호 — 비어 있으면 앱이 전화 버튼을 그리지 않는다.
     * 현장에서 장비가 죽었을 때 "영업일 1일 안에 답변" 밖에 길이 없던 것 (사용성 검토 2026-09-22).
     */
    @Transactional(readOnly = true)
    public String supportPhone() {
        // app_settings 의 JSON 값을 문자열로 읽는다. V020 이 넣은 기본값은 빈 문자열("") → 아래에서 null 로 바꿔 보낸다.
        // 키 자체가 없으면 AppSettingsService 가 INTERNAL(500)을 던진다 — 두 앱의 getSupportPhone 은 오류면 null 로 본다
        String phone = settings.get("support_phone", String.class);
        return phone == null || phone.isBlank() ? null : phone;
    }

    /** 내 문의와 답변 (최신순) */
    @Transactional(readOnly = true)
    public List<MyInquiryResponse> myInquiries(UUID userId) {
        // 답변 전이면 answeredAt 이 null 이라 answeredLabel 도 null 로 보낸다
        return inquiries.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(i -> new MyInquiryResponse(i.getId(), i.getContent(), i.getStatus(), i.getAnswer(), label(i.getCreatedAt()), i.getAnsweredAt() == null ? null : label(i.getAnsweredAt())))
                .toList();
    }

    /** 운영자 답변(관리 API 전까지 X-Admin-Token) → 사용자에게 InquiryAnswered 푸시 */
    @Transactional
    public MyInquiryResponse answer(UUID inquiryId, String answer) {
        // 없는 문의면 404. 이미 답한 문의에 다시 보내면 답변을 덮어쓰고 InquiryAnswered 행도 한 번 더 적힌다 (중복을 막지 않는다).
        // 푸시는 아웃박스 행으로만 남긴다 (CODE_GUIDE.md 3-6)
        Inquiry i = inquiries.findById(inquiryId).orElseThrow(ApiException::notFound);
        i.answer(answer.trim(), clock.now());
        outbox.publish(new InquiryAnswered(i.getId(), i.getUserId()));
        return new MyInquiryResponse(i.getId(), i.getContent(), i.getStatus(), i.getAnswer(), label(i.getCreatedAt()), label(i.getAnsweredAt()));
    }

    /** 저장된 시각(UTC Instant) → 서울 시각 기준 "9월 18일 오후 2:00" 모양의 표시 문자열 (KoreanFormat.dateTimeShort) */
    private static String label(java.time.Instant at) {
        java.time.ZonedDateTime z = AppClock.toSeoul(at);
        return KoreanFormat.dateTimeShort(z.toLocalDate(), z.toLocalTime());
    }

    /** 대상(customer|manager)의 FAQ 를 sort 순으로 [{ id, question, answer }]. audience 가 둘 중 하나가 아니면 400 */
    @Transactional(readOnly = true)
    public List<FaqResponse> faq(String audience) {
        TermsService.requireAudience(audience);
        return faqs.findByAudienceOrderBySort(audience).stream().map(f -> new FaqResponse(f.getId(), f.getQuestion(), f.getAnswer())).toList();
    }

    /** 대상 + all, 최신순 */
    @Transactional(readOnly = true)
    public List<NoticeSummary> notices(String audience) {
        // audience 는 customer·manager 만 받는다 ("all" 로는 부를 수 없다). date 는 서울 날짜 "2026.09.10"
        TermsService.requireAudience(audience);
        return notices.findVisible(audience).stream().map(n -> new NoticeSummary(n.getId(), n.getTitle(), KoreanFormat.noticeDate(n.getPublishedAt()))).toList();
    }

    /** 다른 대상의 공지는 존재를 노출하지 않는다(404) */
    @Transactional(readOnly = true)
    public NoticeResponse notice(UUID id, String audience) {
        TermsService.requireAudience(audience);
        Notice n = notices.findById(id).filter(x -> x.visibleTo(audience)).orElseThrow(ApiException::notFound);
        return new NoticeResponse(n.getId(), n.getTitle(), KoreanFormat.noticeDate(n.getPublishedAt()), n.getBody());
    }

    /** 공백만이면 VALIDATION, 2,000자 상한(dto). Idempotency-Key 가 있으면 재시도가 문의를 두 번 남기지 않는다 (§11-9) */
    public InquiryResponse submit(UUID userId, String idempotencyKey, String content) {
        // 저장하는 글은 앞뒤 공백을 뗀 값이다. 컨트롤러의 @Valid 와 별개로 여기서도 비었는지·길이를 다시 본다
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) throw ApiException.validation("문의 내용을 입력해주세요");
        if (text.length() > Inquiry.MAX_LENGTH) throw ApiException.validation("문의는 2,000자까지 쓸 수 있어요");
        // 멱등 비교에 쓰는 내용은 공백을 뗀 글(text)이라, 앞뒤 공백만 다른 재시도도 같은 요청으로 본다 (CODE_GUIDE.md 3-7).
        // 속도 제한·탈퇴 잠금·저장은 람다 안이라 첫 요청에서만 실행된다. 키가 없으면 멱등 처리 없이 그대로 실행된다
        return idempotent.run(idempotencyKey, userId, "inquiry:" + text, InquiryResponse.class, () -> {
            rateLimiter.check("inquiry", userId.toString(), RateLimiter.INQUIRY_PER_USER_PER_HOUR); // 같은 키 재시도(저장된 응답)는 세지 않는다 (감사 M-05)
            profiles.holdLive(userId); // 탈퇴와 줄을 선다 — 탈퇴 뒤에 커밋된 문의는 보관 기한(3년)이 안 적혀 5년 파기(DeletedAccountPurger) 때까지 남는다 (자체 점검 #31). 탈퇴가 먼저면 401
            return new InquiryResponse(inquiries.save(Inquiry.open(userId, text, clock.now())).getId());
        });
    }

    /** 탈퇴 (AuthService.deleteAccount 의 트랜잭션 안): 이 사용자의 문의를 탈퇴 + 3년 뒤 지우도록 적는다 */
    public void retainAfterDeletion(UUID userId) {
        // Instant 에는 '년' 단위 더하기가 없어 서울 시간대 날짜로 바꿔 3년을 더한 뒤 다시 Instant 로 돌린다.
        // 이미 기한이 적힌 문의는 그대로 둔다 (retainUntil 의 조건)
        inquiries.retainUntil(userId, clock.now().atZone(AppClock.ZONE).plusYears(INQUIRY_RETENTION_YEARS).toInstant());
    }

    /** 탈퇴 + 5년 파기 — 3년 보관 정리가 놓친 문의까지 (DeletedAccountPurger) */
    public void forgetInquiries(UUID userId) {
        inquiries.deleteByUserId(userId);
    }

    /** 보관 기간(탈퇴 + 3년)이 지난 문의를 지운다 — 매일 InquiryRetention */
    // 반환값 = 지운 문의 수 (InquiryRetention 이 1건 이상이면 로그에 남긴다)
    public int purgeExpiredInquiries() {
        return inquiries.deletePurgeable(clock.now());
    }
}
