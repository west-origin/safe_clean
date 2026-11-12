package kr.ansimclean.api.support;

import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.Idempotent;
import kr.ansimclean.api.support.dto.AdminNoticeRequest;
import kr.ansimclean.api.support.dto.AdminNoticeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 어드민 공지 관리 (rbac.md §10): 어드민 + MFA 만, 입력 검사, 등록·수정·삭제마다 활동 이력 */
class AdminNoticeServiceTest {
    static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z"); // 서울 2026-11-12 12:00
    NoticeRepository notices = mock(NoticeRepository.class);
    ActivityLogService activity = mock(ActivityLogService.class);
    Idempotent idempotent = mock(Idempotent.class);
    AdminNoticeService service = new AdminNoticeService(notices, new AccessPolicy(), activity, idempotent, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    CurrentUser admin = new CurrentUser(UUID.randomUUID(), Roles.ADMIN, true, null, true);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(idempotent.run(any(), any(), any(), any(), any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(4)).get());
        when(notices.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void 공지를_올리면_공백을_떼고_오늘_날짜로_저장하고_이력을_남긴다() {
        AdminNoticeResponse res = service.create(admin, "k1", new AdminNoticeRequest("customer", "  설 연휴 안내 ", "\n연휴에도 예약할 수 있어요\n"));
        assertThat(res.audience()).isEqualTo("customer");
        assertThat(res.title()).isEqualTo("설 연휴 안내");
        assertThat(res.body()).isEqualTo("연휴에도 예약할 수 있어요");
        assertThat(res.date()).isEqualTo("2026.11.12");
        verify(activity).record(eq(admin), eq(ActivityLogService.NOTICE_CREATE), eq("notice"), eq(res.id().toString()), isNull(),
                eq(Map.of("audience", "customer", "title", "설 연휴 안내", "body", "연휴에도 예약할 수 있어요")), isNull());
    }

    @Test
    void 고치면_날짜는_그대로이고_바꾸기_전후를_이력에_남긴다() {
        Notice n = Notice.publish("all", "옛 제목", "옛 본문", Instant.parse("2026-09-10T00:00:00Z"));
        when(notices.findById(n.getId())).thenReturn(Optional.of(n));
        AdminNoticeResponse res = service.update(admin, n.getId(), new AdminNoticeRequest("manager", "새 제목", "새 본문"));
        assertThat(res.date()).isEqualTo("2026.09.10");
        assertThat(n.getAudience()).isEqualTo("manager");
        verify(activity).record(admin, ActivityLogService.NOTICE_UPDATE, "notice", n.getId().toString(),
                Map.of("audience", "all", "title", "옛 제목", "body", "옛 본문"), Map.of("audience", "manager", "title", "새 제목", "body", "새 본문"), null);
    }

    @Test
    void 지우면_지운_내용을_이력에_남기고_없는_공지는_404() {
        Notice n = Notice.publish("customer", "t", "b", NOW);
        when(notices.findById(n.getId())).thenReturn(Optional.of(n));
        service.delete(admin, n.getId());
        verify(notices).delete(n);
        verify(activity).record(admin, ActivityLogService.NOTICE_DELETE, "notice", n.getId().toString(), Map.of("audience", "customer", "title", "t", "body", "b"), null, null);
        assertThatThrownBy(() -> service.delete(admin, UUID.randomUUID())).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 입력이_규칙에_어긋나면_VALIDATION_이고_저장하지_않는다() {
        String longTitle = "가".repeat(Notice.TITLE_MAX + 1);
        for (AdminNoticeRequest bad : new AdminNoticeRequest[] {
                new AdminNoticeRequest("everyone", "t", "b"), new AdminNoticeRequest("customer", "  ", "b"),
                new AdminNoticeRequest("customer", "t", " "), new AdminNoticeRequest("customer", longTitle, "b"),
                new AdminNoticeRequest("customer", "t", "가".repeat(Notice.BODY_MAX + 1)), null }) {
            assertThatThrownBy(() -> service.create(admin, null, bad)).isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        }
        verify(notices, never()).save(any());
    }

    @Test
    void 어드민_MFA_가_아니면_403_이고_아무것도_바꾸지_않는다() {
        CurrentUser noMfa = new CurrentUser(admin.id(), Roles.ADMIN, true, null, false);
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), Roles.MANAGER, true, "active", true);
        for (CurrentUser u : new CurrentUser[] { noMfa, manager }) {
            assertThatThrownBy(() -> service.list(u)).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> service.create(u, null, new AdminNoticeRequest("customer", "t", "b"))).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> service.delete(u, UUID.randomUUID())).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        }
        verify(notices, never()).save(any());
        verify(notices, never()).delete(any());
    }
}
