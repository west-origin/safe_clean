package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CleaningPhotoResponse;
import kr.ansimclean.api.booking.dto.CustomerCleaningPhotosResponse;
import kr.ansimclean.api.booking.dto.JobPhotosResponse;
import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import kr.ansimclean.api.common.time.AppClock;
import kr.ansimclean.api.common.web.RateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 청소 전·후 사진 서비스 단위 테스트. Storage·원장 레포지토리·BookingService·설정은 Mockito 목,
 * 시간당 한도(RateLimiter)는 진짜(고정 시계), 트랜잭션은 목 트랜잭션 관리자로 TransactionTemplate 을 만든다(람다는 그대로 실행된다).
 * 보는 것: 위치 이름·10곳·진행 중 상태·남의 작업 검사, 업로드 → 행 저장 순서, 저장 실패 때 방금 올린 파일 삭제,
 * '후' 사진 바꾸기, 고객 ready·짝 걸러내기, 서명 URL 실패 → null, 탈퇴·보존 기간 정리.
 */
class CleaningPhotoServiceTest {
    static final Instant NOW = Instant.parse("2026-09-17T02:00:00Z"); // 서울 11:00
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
    RateLimiter rateLimiter = new RateLimiter(new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    BookingService bookings = mock(BookingService.class);
    CleaningPhotoRepository photos = mock(CleaningPhotoRepository.class);
    AppSettingsService settings = mock(AppSettingsService.class);
    TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
    RecordingLookup recordings = mock(RecordingLookup.class);
    CleaningPhotoService service = new CleaningPhotoService(storage, rateLimiter, bookings, photos, settings, new AppClock(Clock.fixed(NOW, AppClock.ZONE)), tx, recordings);

    // 기본: 서명 URL 은 경로 앞에 'signed:' 를 붙인 가짜 주소, save 는 받은 행을 그대로 돌려준다
    {
        when(storage.signedUrl(eq(CleaningPhotoService.BUCKET), anyString(), anyInt())).thenAnswer(i -> "signed:" + i.getArgument(1));
        when(photos.save(any())).thenAnswer(i -> i.getArgument(0));
        when(recordings.isVideoEnded(any())).thenReturn(true);
    }

    /** 진행 중인 이 매니저의 작업을 만들고 BookingService 목이 돌려주게 한다 */
    Booking active() {
        Booking b = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        stub(b);
        return b;
    }

    void stub(Booking b) {
        b.setRequiredPhotoRooms(List.of("거실", "거실 소파 앞"));
        when(bookings.requireManagerBooking(MANAGER, b.getId())).thenReturn(b);
        when(bookings.lockManagerBooking(MANAGER, b.getId())).thenReturn(b);
        when(bookings.requireCustomerBooking(CUSTOMER, b.getId())).thenReturn(b);
    }

    static byte[] png() {
        return RepairPhotoServiceTest.png(10, 10);
    }

    static ErrorCode code(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e.code();
        }
        throw new AssertionError("ApiException 이 나지 않았다");
    }

    // ── 전 사진 ──

    @Test
    void 전_사진은_Storage_에_먼저_올리고_행을_저장한다() {
        Booking b = active();
        when(photos.maxSort(b.getId())).thenReturn(2);

        CleaningPhotoResponse res = service.addBefore(MANAGER, b.getId(), "  거실 소파 앞  ", "image/png", png());

        ArgumentCaptor<CleaningPhoto> saved = ArgumentCaptor.forClass(CleaningPhoto.class);
        InOrder order = inOrder(storage, bookings, photos);
        order.verify(storage).upload(eq(CleaningPhotoService.BUCKET), anyString(), any(), eq("image/png"));
        order.verify(bookings).lockManagerBooking(MANAGER, b.getId()); // 트랜잭션 안에서 잠그고 다시 확인
        order.verify(photos).save(saved.capture());
        CleaningPhoto p = saved.getValue();
        assertThat(p.getBeforePath()).isEqualTo(b.getId() + "/" + p.getId() + "-before.png");
        assertThat(p.getLocation()).isEqualTo("거실 소파 앞"); // 앞뒤 공백을 뺀다
        assertThat(p.getSort()).isEqualTo(3);
        assertThat(p.getBeforeAt()).isEqualTo(NOW);
        assertThat(res.id()).isEqualTo(p.getId());
        assertThat(res.location()).isEqualTo("거실 소파 앞");
        assertThat(res.beforeUrl()).isEqualTo("signed:" + p.getBeforePath());
        assertThat(res.afterUrl()).isNull();
    }

    @Test
    void 위치_이름은_공백_뺀_1자에서_20자() {
        Booking b = active();
        b.setRequiredPhotoRooms(List.of("거실", "가".repeat(20)));
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), "   ", "image/png", png())).hasMessage(CleaningPhotoService.BAD_LOCATION);
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), null, "image/png", png())).hasMessage(CleaningPhotoService.BAD_LOCATION);
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), "가".repeat(21), "image/png", png())).hasMessage(CleaningPhotoService.BAD_LOCATION);
        verify(storage, never()).upload(any(), any(), any(), any());
        // 20자는 통과. 이모지도 한 글자로 센다(DB char_length 와 같게)
        service.addBefore(MANAGER, b.getId(), "가".repeat(20), "image/png", png());
        assertThat(CleaningPhotoService.normalizeLocation("🧹".repeat(20))).hasSize(40); // UTF-16 으로는 40 이지만 통과
    }

    @Test
    void 위치_10곳을_넘으면_올리지_않는다() {
        Booking b = active();
        when(photos.countByBookingId(b.getId())).thenReturn(10L);
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", png()))
                .isInstanceOf(ApiException.class).hasMessage(CleaningPhotoService.TOO_MANY);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 진행_중이_아니면_INVALID_STATE() {
        Booking scheduled = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        stub(scheduled);
        assertThat(code(() -> service.addBefore(MANAGER, scheduled.getId(), "거실", "image/png", png()))).isEqualTo(ErrorCode.INVALID_STATE);
        Booking done = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        done.complete(NOW);
        stub(done);
        assertThatThrownBy(() -> service.addBefore(MANAGER, done.getId(), "거실", "image/png", png())).hasMessage(CleaningPhotoService.NOT_ACTIVE);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 일시정지_중에도_찍을_수_있다() {
        Booking b = active();
        b.pause();
        assertThat(service.addBefore(MANAGER, b.getId(), "거실", "image/png", png()).location()).isEqualTo("거실");
    }

    @Test
    void 남의_작업은_NOT_FOUND() {
        UUID other = UUID.randomUUID();
        when(bookings.requireManagerBooking(MANAGER, other)).thenThrow(ApiException.notFound());
        assertThat(code(() -> service.addBefore(MANAGER, other, "거실", "image/png", png()))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(code(() -> service.managerList(MANAGER, other))).isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    void 이미지가_아니면_VALIDATION() {
        Booking b = active();
        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "text/html", png()))).isEqualTo(ErrorCode.VALIDATION);
        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", "<html>".getBytes()))).isEqualTo(ErrorCode.VALIDATION);
        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", new byte[0]))).isEqualTo(ErrorCode.VALIDATION);
        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", new byte[(int) RepairPhotoService.MAX_BYTES + 1]))).isEqualTo(ErrorCode.VALIDATION);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 시간당_상한을_넘기면_RATE_LIMITED() {
        Booking b = active();
        for (int i = 0; i < CleaningPhotoService.UPLOADS_PER_MANAGER_PER_HOUR; i++) service.addBefore(MANAGER, b.getId(), "거실", "image/png", png());
        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", png()))).isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void 올리는_사이_작업이_끝나면_방금_올린_파일을_지우고_INVALID_STATE() {
        Booking b = active();
        Booking finished = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        finished.complete(NOW);
        when(bookings.requireManagerBooking(MANAGER, b.getId())).thenReturn(b); // 처음 확인 때는 진행 중
        when(bookings.lockManagerBooking(MANAGER, b.getId())).thenReturn(finished); // 잠그고 보니 완료됨

        assertThat(code(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", png()))).isEqualTo(ErrorCode.INVALID_STATE);

        ArgumentCaptor<String> uploaded = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(eq(CleaningPhotoService.BUCKET), uploaded.capture(), any(), any());
        verify(storage).delete(CleaningPhotoService.BUCKET, uploaded.getValue());
        verify(photos, never()).save(any());
    }

    @Test
    void 행_저장이_실패하면_파일을_지우고_그대로_던진다_파일_삭제_실패는_삼킨다() {
        Booking b = active();
        when(photos.save(any())).thenThrow(new IllegalStateException("db down"));
        doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(any(), any());
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", png())).isInstanceOf(IllegalStateException.class);
        verify(storage).delete(eq(CleaningPhotoService.BUCKET), anyString());
    }

    // ── 후 사진 ──

    @Test
    void 후_사진은_새_경로로_올리고_예전_파일은_커밋_뒤_지운다() {
        Booking b = active();
        b.confirmBeforePhotos(NOW); b.finishVideo(NOW);
        CleaningPhoto p = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "거실", 1, b.getId() + "/x-before.png", NOW);
        p.replaceAfter(b.getId() + "/x-after-old.png", NOW);
        when(photos.findByIdAndBookingId(p.getId(), b.getId())).thenReturn(Optional.of(p));

        CleaningPhotoResponse res = service.addAfter(MANAGER, b.getId(), p.getId(), "image/jpeg", RepairPhotoServiceTest.jpeg(10, 10));

        assertThat(p.getAfterPath()).matches(b.getId() + "/" + p.getId() + "-after-[0-9a-f]{8}\\.jpg");
        assertThat(p.getAfterAt()).isEqualTo(NOW);
        assertThat(res.afterUrl()).isEqualTo("signed:" + p.getAfterPath());
        assertThat(res.beforeUrl()).isEqualTo("signed:" + p.getBeforePath());
        InOrder order = inOrder(storage);
        order.verify(storage).upload(CleaningPhotoService.BUCKET, p.getAfterPath(), RepairPhotoServiceTest.jpeg(10, 10), "image/jpeg");
        order.verify(storage).delete(CleaningPhotoService.BUCKET, b.getId() + "/x-after-old.png");
    }

    @Test
    void 없는_사진의_후_사진은_NOT_FOUND() {
        Booking b = active();
        b.confirmBeforePhotos(NOW); b.finishVideo(NOW);
        UUID unknown = UUID.randomUUID();
        when(photos.findByIdAndBookingId(unknown, b.getId())).thenReturn(Optional.empty());
        assertThat(code(() -> service.addAfter(MANAGER, b.getId(), unknown, "image/png", png()))).isEqualTo(ErrorCode.NOT_FOUND);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

    @Test
    void 후_사진도_진행_중에만() {
        Booking b = TestBookings.inProgress(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0), NOW);
        b.complete(NOW);
        stub(b);
        assertThat(code(() -> service.addAfter(MANAGER, b.getId(), UUID.randomUUID(), "image/png", png()))).isEqualTo(ErrorCode.INVALID_STATE);
    }

    // ── 지우기 ──

    @Test
    void 지우기는_행을_먼저_지우고_파일은_뒤에_실패해도_성공() {
        Booking b = active();
        CleaningPhoto p = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "거실", 1, "a-before.png", NOW);
        p.replaceAfter("a-after.png", NOW);
        when(photos.findByIdAndBookingId(p.getId(), b.getId())).thenReturn(Optional.of(p));
        doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(CleaningPhotoService.BUCKET, "a-before.png");

        service.delete(MANAGER, b.getId(), p.getId());

        InOrder order = inOrder(photos, storage);
        order.verify(photos).delete(p);
        order.verify(storage).delete(CleaningPhotoService.BUCKET, "a-before.png");
        order.verify(storage).delete(CleaningPhotoService.BUCKET, "a-after.png");
    }

    @Test
    void 없는_사진_지우기는_NOT_FOUND() {
        Booking b = active();
        assertThat(code(() -> service.delete(MANAGER, b.getId(), UUID.randomUUID()))).isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(storage);
    }

    // ── 목록 ──

    @Test
    void 매니저_목록은_진행_중과_완료만_순서대로_max_10() {
        Booking b = active();
        CleaningPhoto first = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "거실", 1, "1-before.png", NOW);
        CleaningPhoto second = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "주방", 2, "2-before.png", NOW);
        second.replaceAfter("2-after.png", NOW);
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(first, second));

        JobPhotosResponse res = service.managerList(MANAGER, b.getId());
        assertThat(res.max()).isEqualTo(10);
        assertThat(res.photos()).extracting(CleaningPhotoResponse::location).containsExactly("거실", "주방");
        assertThat(res.photos().get(0).afterUrl()).isNull();
        assertThat(res.photos().get(1).afterUrl()).isEqualTo("signed:2-after.png");

        b.complete(NOW); // 끝난 작업도 볼 수 있다
        assertThat(service.managerList(MANAGER, b.getId()).photos()).hasSize(2);

        Booking scheduled = TestBookings.scheduled(CUSTOMER, MANAGER, TODAY, LocalTime.of(10, 0));
        stub(scheduled);
        assertThat(code(() -> service.managerList(MANAGER, scheduled.getId()))).isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void 고객은_끝나기_전에는_ready_false_빈_목록() {
        Booking b = active();
        CustomerCleaningPhotosResponse res = service.customerList(CUSTOMER, b.getId());
        assertThat(res.ready()).isFalse();
        assertThat(res.photos()).isEmpty();
        verify(photos, never()).findByBookingIdOrderBySortAsc(any());
    }

    @Test
    void 고객은_끝난_뒤_전후가_다_있는_위치만_보고_서명_URL_실패는_null() {
        Booking b = active();
        b.complete(NOW);
        CleaningPhoto onlyBefore = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "거실", 1, "1-before.png", NOW);
        CleaningPhoto paired = CleaningPhoto.of(UUID.randomUUID(), b.getId(), "주방", 2, "2-before.png", NOW);
        paired.replaceAfter("2-after.png", NOW);
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(onlyBefore, paired));
        when(storage.signedUrl(CleaningPhotoService.BUCKET, "2-before.png", CleaningPhotoService.URL_SECONDS)).thenThrow(new ApiException(ErrorCode.UPSTREAM));

        CustomerCleaningPhotosResponse res = service.customerList(CUSTOMER, b.getId());

        assertThat(res.ready()).isTrue();
        assertThat(res.photos()).hasSize(1);
        CleaningPhotoResponse photo = res.photos().get(0);
        assertThat(photo.id()).isEqualTo(paired.getId());
        assertThat(photo.beforeUrl()).isNull(); // 이 칸만 null, 목록은 그대로
        assertThat(photo.afterUrl()).isEqualTo("signed:2-after.png");
    }

    @Test
    void 남의_예약은_고객에게도_NOT_FOUND() {
        UUID other = UUID.randomUUID();
        when(bookings.requireCustomerBooking(CUSTOMER, other)).thenThrow(ApiException.notFound());
        assertThat(code(() -> service.customerList(CUSTOMER, other))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ── 정리 ──

    @Test
    void 탈퇴는_원장과_예약_폴더의_파일을_지우고_행을_지운다() {
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        CleaningPhoto p = CleaningPhoto.of(UUID.randomUUID(), b1, "거실", 1, b1 + "/a-before.png", NOW);
        p.replaceAfter(b1 + "/a-after-1.png", NOW);
        when(photos.findByBookingIdIn(List.of(b1, b2))).thenReturn(List.of(p));
        when(storage.list(CleaningPhotoService.BUCKET, b1.toString())).thenReturn(List.of(b1 + "/a-before.png", b1 + "/a-after-0.png"));
        when(storage.list(CleaningPhotoService.BUCKET, b2.toString())).thenReturn(List.of());

        assertThat(service.deleteAllOfBookings(List.of(b1, b2))).isEqualTo(3); // 겹치는 경로는 한 번만

        verify(storage).delete(CleaningPhotoService.BUCKET, b1 + "/a-before.png");
        verify(storage).delete(CleaningPhotoService.BUCKET, b1 + "/a-after-1.png");
        verify(storage).delete(CleaningPhotoService.BUCKET, b1 + "/a-after-0.png"); // 다시 찍기로 남은 파일
        verify(photos).deleteAll(List.of(p));
    }

    @Test
    void 탈퇴_정리의_Storage_실패는_던지고_행은_남긴다() {
        UUID b1 = UUID.randomUUID();
        CleaningPhoto p = CleaningPhoto.of(UUID.randomUUID(), b1, "거실", 1, b1 + "/a-before.png", NOW);
        when(photos.findByBookingIdIn(List.of(b1))).thenReturn(List.of(p));
        doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(any(), any());
        assertThat(code(() -> service.deleteAllOfBookings(List.of(b1)))).isEqualTo(ErrorCode.UPSTREAM);
        verify(photos, never()).deleteAll(any());
    }

    @Test
    void 탈퇴_정리는_예약이_없으면_아무것도_안_부른다() {
        assertThat(service.deleteAllOfBookings(List.of())).isZero();
        verifyNoInteractions(storage, photos);
    }

    @Test
    void 보존_기간_정리는_예약마다_지우고_실패한_예약은_건너뛴다() {
        when(settings.integer(CleaningPhotoService.SETTING_RETENTION_DAYS)).thenReturn(90);
        UUID failing = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        CleaningPhoto bad = CleaningPhoto.of(UUID.randomUUID(), failing, "거실", 1, failing + "/x-before.png", NOW);
        CleaningPhoto good1 = CleaningPhoto.of(UUID.randomUUID(), ok, "거실", 1, ok + "/y-before.png", NOW);
        CleaningPhoto good2 = CleaningPhoto.of(UUID.randomUUID(), ok, "주방", 2, ok + "/z-before.png", NOW);
        good2.replaceAfter(ok + "/z-after-1.png", NOW);
        when(photos.findExpired(NOW.minus(java.time.Duration.ofDays(90)), CleaningPhotoService.RETENTION_BATCH)).thenReturn(List.of(bad, good1, good2));
        when(storage.list(any(), any())).thenReturn(List.of());
        doThrow(new ApiException(ErrorCode.UPSTREAM)).when(storage).delete(CleaningPhotoService.BUCKET, failing + "/x-before.png");

        assertThat(service.deleteExpired()).isEqualTo(2);

        verify(photos, never()).deleteAll(List.of(bad));
        verify(storage).delete(CleaningPhotoService.BUCKET, ok + "/z-after-1.png");
        verify(photos).deleteAll(List.of(good1, good2));
    }

    @Test
    void 영상_진행_중에는_전_사진_변경과_후_사진_등록을_거부한다() {
        Booking b = active(); b.confirmBeforePhotos(NOW);
        assertThatThrownBy(() -> service.addBefore(MANAGER, b.getId(), "거실", "image/png", png())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(MANAGER, b.getId(), UUID.randomUUID())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.addAfter(MANAGER, b.getId(), UUID.randomUUID(), "image/png", png())).isInstanceOf(ApiException.class);
        b.finishVideo(NOW);
        when(recordings.isVideoEnded(b.getId())).thenReturn(false);
        assertThatThrownBy(() -> service.addAfter(MANAGER, b.getId(), UUID.randomUUID(), "image/png", png())).isInstanceOf(ApiException.class);
        verify(storage, never()).upload(any(), any(), any(), any());
    }

}
