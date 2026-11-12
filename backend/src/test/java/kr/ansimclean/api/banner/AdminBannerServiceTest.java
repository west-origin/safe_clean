package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.AdminBannerResponse;
import kr.ansimclean.api.banner.dto.AdminBannerTextRequest;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.events.BannerImageDeleted;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 어드민 배너 관리 (rbac.md §15-22): 어드민 + MFA 만, 이미지·문구·기간 검사, 맨 뒤에 붙기, 순서 바꾸기, 옛 파일은 아웃박스로 */
class AdminBannerServiceTest {
    static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z"); // 서울 11월 12일
    HomeBannerRepository repo = mock(HomeBannerRepository.class);
    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    ActivityLogService activity = mock(ActivityLogService.class);
    OutboxService outbox = mock(OutboxService.class);
    AdminBannerService service = new AdminBannerService(repo, new BannerImages(storage), new AccessPolicy(), activity, outbox,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    CurrentUser admin = new CurrentUser(UUID.randomUUID(), Roles.ADMIN, true, null, true);
    List<HomeBanner> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> { stored.add(inv.getArgument(0)); return inv.getArgument(0); });
        when(repo.lockAllInOrder()).thenAnswer(inv -> List.copyOf(stored));
        when(storage.signedUrl(eq(BannerImages.BUCKET), anyString(), anyInt())).thenAnswer(inv -> "https://signed/" + inv.getArgument(1));
    }

    AdminBannerResponse create(String title, String startsOn, String endsOn) {
        return service.create(admin, "image/png", BannerTestImages.png(36, 11), new AdminBannerTextRequest(title, "설명", startsOn, endsOn));
    }

    @Test
    void 새_배너는_이미지를_새_경로로_올리고_맨_뒤에_붙고_이력을_남긴다() {
        AdminBannerResponse first = create("첫 청소 쿠폰", null, null);
        AdminBannerResponse second = create(" 선물하기 ", "2026-11-01", "2026-12-31");
        assertThat(stored).extracting(HomeBanner::getSortOrder).containsExactly(1, 2);
        assertThat(second.title()).isEqualTo("선물하기");
        assertThat(second.imageUrl()).startsWith("https://signed/" + second.id() + "/").endsWith(".png");
        assertThat(second.periodLabel()).isEqualTo("11월 1일 ~ 12월 31일");
        assertThat(second.state()).isEqualTo("live");
        assertThat(first.periodLabel()).isEqualTo("기간 제한 없음");
        verify(storage).upload(eq(BannerImages.BUCKET), startsWith(first.id() + "/"), any(), eq("image/png"));
        verify(activity).record(eq(admin), eq(ActivityLogService.BANNER_CREATE), eq("banner"), eq(first.id().toString()), any(), any(), any());
    }

    @Test
    void 게시_상태는_오늘_서울_날짜로_정한다() {
        assertThat(create("예정", "2026-11-13", null)).extracting(AdminBannerResponse::state, AdminBannerResponse::stateLabel, AdminBannerResponse::periodLabel)
                .containsExactly("scheduled", "게시 예정", "11월 13일부터");
        assertThat(create("종료", null, "2026-11-11")).extracting(AdminBannerResponse::state, AdminBannerResponse::stateLabel, AdminBannerResponse::periodLabel)
                .containsExactly("ended", "게시 종료", "11월 11일까지");
        assertThat(create("오늘만", "2026-11-12", "2026-11-12").state()).isEqualTo("live"); // 양 끝 포함
    }

    @Test
    void 이미지_문구_기간이_규칙에_어긋나면_VALIDATION_이고_올리지_않는다() {
        AdminBannerTextRequest ok = new AdminBannerTextRequest("제목", "", null, null);
        byte[] png = BannerTestImages.png(36, 11);
        assertThatThrownBy(() -> service.create(admin, "image/png", null, ok)).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.create(admin, "image/heic", png, ok)).extracting("code").isEqualTo(ErrorCode.VALIDATION); // 고객 앱이 그리지 못한다
        assertThatThrownBy(() -> service.create(admin, "image/png", "not an image".getBytes(), ok)).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.create(admin, "image/png", new byte[(int) BannerImages.MAX_BYTES + 1], ok)).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        for (AdminBannerTextRequest bad : new AdminBannerTextRequest[] {
                new AdminBannerTextRequest(" ", "", null, null), new AdminBannerTextRequest("가".repeat(41), "", null, null),
                new AdminBannerTextRequest("제목", "가".repeat(61), null, null), new AdminBannerTextRequest("제목", "", "11/12", null),
                new AdminBannerTextRequest("제목", "", "2026-12-01", "2026-11-30") }) {
            assertThatThrownBy(() -> service.create(admin, "image/png", png, bad)).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        }
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void DB_저장이_실패하면_방금_올린_파일을_치운다() {
        doThrow(new RuntimeException("db down")).when(repo).save(any()); // doThrow: 위 setUp 의 답을 부르지 않고 바꾼다
        assertThatThrownBy(() -> create("제목", null, null)).hasMessage("db down");
        verify(storage).delete(eq(BannerImages.BUCKET), anyString());
    }

    @Test
    void 이미지를_바꾸면_새_경로로_올리고_옛_파일은_아웃박스로_지운다() {
        AdminBannerResponse b = create("제목", null, null);
        HomeBanner row = stored.get(0);
        String old = row.getImagePath();
        when(repo.findById(b.id())).thenReturn(Optional.of(row));
        service.replaceImage(admin, b.id(), "image/png", BannerTestImages.png(36, 11));
        assertThat(row.getImagePath()).isNotEqualTo(old).startsWith(b.id() + "/");
        verify(outbox).publish(new BannerImageDeleted(old));
        verify(activity).record(eq(admin), eq(ActivityLogService.BANNER_IMAGE), eq("banner"), eq(b.id().toString()), any(), any(), any());
    }

    @Test
    void 순서는_모든_id_를_받아_1부터_다시_매기고_빠지거나_남으면_VALIDATION() {
        AdminBannerResponse a = create("A", null, null);
        AdminBannerResponse b = create("B", null, null);
        AdminBannerResponse c = create("C", null, null);
        List<AdminBannerResponse> res = service.reorder(admin, List.of(c.id(), a.id(), b.id()));
        assertThat(res).extracting(AdminBannerResponse::title).containsExactly("C", "A", "B");
        assertThat(stored).extracting(HomeBanner::getTitle, HomeBanner::getSortOrder).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("C", 1), org.assertj.core.groups.Tuple.tuple("A", 2), org.assertj.core.groups.Tuple.tuple("B", 3));
        assertThatThrownBy(() -> service.reorder(admin, List.of(a.id(), b.id()))).extracting("code").isEqualTo(ErrorCode.VALIDATION);
        assertThatThrownBy(() -> service.reorder(admin, List.of(a.id(), b.id(), UUID.randomUUID()))).extracting("code").isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void 지우면_행을_지우고_파일은_아웃박스로_이력을_남긴다() {
        AdminBannerResponse b = create("제목", null, null);
        HomeBanner row = stored.get(0);
        when(repo.findById(b.id())).thenReturn(Optional.of(row));
        service.delete(admin, b.id());
        verify(repo).delete(row);
        verify(outbox).publish(new BannerImageDeleted(row.getImagePath()));
        verify(activity).record(eq(admin), eq(ActivityLogService.BANNER_DELETE), eq("banner"), eq(b.id().toString()), any(), eq(null), eq(null));
        assertThatThrownBy(() -> service.delete(admin, UUID.randomUUID())).extracting("code").isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 어드민_MFA_가_아니면_403_이고_파일을_올리지_않는다() {
        CurrentUser noMfa = new CurrentUser(admin.id(), Roles.ADMIN, true, null, false);
        CurrentUser manager = new CurrentUser(UUID.randomUUID(), Roles.MANAGER, true, "active", true);
        for (CurrentUser u : new CurrentUser[] { noMfa, manager }) {
            assertThatThrownBy(() -> service.list(u)).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> service.create(u, "image/png", BannerTestImages.png(36, 11), new AdminBannerTextRequest("t", "", null, null)))
                    .extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            assertThatThrownBy(() -> service.delete(u, UUID.randomUUID())).extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
        }
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 기간_문구() {
        assertThat(AdminBannerService.periodLabel(LocalDate.of(2026, 1, 2), null)).isEqualTo("1월 2일부터");
    }
}
