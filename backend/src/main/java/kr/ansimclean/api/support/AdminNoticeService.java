package kr.ansimclean.api.support;

import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.support.dto.AdminNoticeRequest;
import kr.ansimclean.api.support.dto.AdminNoticeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 어드민 공지 관리 (rbac.md §10, 배너·공지 관리 3회차). 매니저 앱 관리자 화면 → /admin/notices/**.
 * 등록한 공지는 고객·매니저 앱이 화면을 열 때 GET /support/notices 로 바로 받는다(앱은 캐시하지 않는다).
 * 모든 메서드는 처음에 AccessPolicy(NOTICE_MANAGE = 어드민 + MFA)를 확인하고, 등록·수정·삭제는 같은 트랜잭션에서 활동 이력을 남긴다.
 */
@Service
@Transactional
public class AdminNoticeService {
    private final NoticeRepository notices;
    private final AccessPolicy policy;
    private final ActivityLogService activity;
    private final Idempotent idempotent;
    private final AppClock clock;

    public AdminNoticeService(NoticeRepository notices, AccessPolicy policy, ActivityLogService activity, Idempotent idempotent, AppClock clock) {
        this.notices = notices;
        this.policy = policy;
        this.activity = activity;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    /** 모든 대상의 공지, 최신순 (본문 없이) */
    @Transactional(readOnly = true)
    public List<AdminNoticeResponse> list(CurrentUser actor) {
        policy.require(actor, AccessPolicy.Action.NOTICE_MANAGE, AccessPolicy.Resource.of("notice", null));
        return notices.findAllLatestFirst().stream()
                .map(n -> new AdminNoticeResponse(n.getId(), n.getAudience(), n.getTitle(), KoreanFormat.noticeDate(n.getPublishedAt()), null)).toList();
    }

    /** 공지 한 건 (본문 포함). 없으면 404 */
    @Transactional(readOnly = true)
    public AdminNoticeResponse get(CurrentUser actor, UUID id) {
        policy.require(actor, AccessPolicy.Action.NOTICE_MANAGE, AccessPolicy.Resource.of("notice", id));
        return detail(notices.findById(id).orElseThrow(ApiException::notFound));
    }

    /** 새 공지를 올린다(게시 시각 = 지금). Idempotency-Key 가 같으면 재시도가 두 번 올리지 않는다 */
    public AdminNoticeResponse create(CurrentUser actor, String idempotencyKey, AdminNoticeRequest req) {
        policy.require(actor, AccessPolicy.Action.NOTICE_MANAGE, AccessPolicy.Resource.of("notice", null));
        Clean c = clean(req);
        return idempotent.run(idempotencyKey, actor.id(), "notice:" + c, AdminNoticeResponse.class, () -> {
            Notice n = notices.save(Notice.publish(c.audience(), c.title(), c.body(), clock.now()));
            activity.record(actor, ActivityLogService.NOTICE_CREATE, "notice", n.getId().toString(), null, snapshot(n), null);
            return detail(n);
        });
    }

    /** 공지 고치기 (대상·제목·본문). 게시 날짜는 그대로. 바꾸기 전·후를 활동 이력에 남긴다 */
    public AdminNoticeResponse update(CurrentUser actor, UUID id, AdminNoticeRequest req) {
        policy.require(actor, AccessPolicy.Action.NOTICE_MANAGE, AccessPolicy.Resource.of("notice", id));
        Clean c = clean(req);
        Notice n = notices.findById(id).orElseThrow(ApiException::notFound);
        Map<String, Object> before = snapshot(n);
        n.edit(c.audience(), c.title(), c.body());
        activity.record(actor, ActivityLogService.NOTICE_UPDATE, "notice", id.toString(), before, snapshot(n), null);
        return detail(n);
    }

    /** 공지 지우기. 지운 내용은 활동 이력(변경 전)에만 남는다. 이미 없으면 404 */
    public void delete(CurrentUser actor, UUID id) {
        policy.require(actor, AccessPolicy.Action.NOTICE_MANAGE, AccessPolicy.Resource.of("notice", id));
        Notice n = notices.findById(id).orElseThrow(ApiException::notFound);
        activity.record(actor, ActivityLogService.NOTICE_DELETE, "notice", id.toString(), snapshot(n), null, null);
        notices.delete(n);
    }

    // 앞뒤 공백을 뗀 입력. toString 은 멱등 비교(같은 요청인가)에 쓴다
    private record Clean(String audience, String title, String body) {}

    /** 대상은 세 값 중 하나, 제목·본문은 공백을 뗀 뒤 비면 안 되고 상한을 넘으면 안 된다. 어기면 VALIDATION(화면에 그대로 보이는 문구) */
    private static Clean clean(AdminNoticeRequest req) {
        String audience = req == null || req.audience() == null ? "" : req.audience().strip();
        String title = req == null || req.title() == null ? "" : req.title().strip();
        String body = req == null || req.body() == null ? "" : req.body().strip();
        if (!Notice.AUDIENCES.contains(audience)) throw ApiException.validation("공지 대상을 골라주세요");
        if (title.isEmpty()) throw ApiException.validation("제목을 입력해주세요");
        if (title.length() > Notice.TITLE_MAX) throw ApiException.validation("제목은 " + Notice.TITLE_MAX + "자까지 쓸 수 있어요");
        if (body.isEmpty()) throw ApiException.validation("내용을 입력해주세요");
        if (body.length() > Notice.BODY_MAX) throw ApiException.validation("내용은 5,000자까지 쓸 수 있어요");
        return new Clean(audience, title, body);
    }

    private static AdminNoticeResponse detail(Notice n) {
        return new AdminNoticeResponse(n.getId(), n.getAudience(), n.getTitle(), KoreanFormat.noticeDate(n.getPublishedAt()), n.getBody());
    }

    /** 활동 이력에 남길 값 (공지는 개인정보가 아니라 전체를 남긴다) */
    private static Map<String, Object> snapshot(Notice n) {
        Map<String, Object> m = new LinkedHashMap<>(); // 순서를 고정해 이력 JSON 을 읽기 쉽게
        m.put("audience", n.getAudience());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        return m;
    }
}
