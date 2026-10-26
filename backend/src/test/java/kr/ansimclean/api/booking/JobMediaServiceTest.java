package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.time.AppClock;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobMediaServiceTest {
    static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
    final UUID manager = UUID.randomUUID();
    final Booking b = TestBookings.inProgress(UUID.randomUUID(), manager, LocalDate.of(2026, 10, 24), LocalTime.NOON, NOW);
    final BookingService bookings = mock(BookingService.class);
    final CleaningPhotoRepository photos = mock(CleaningPhotoRepository.class);
    final JobMediaService service = new JobMediaService(bookings, photos, new AppClock(Clock.fixed(NOW, AppClock.ZONE)));
    JobMediaServiceTest() {
        b.setRequiredPhotoRooms(List.of("거실", "안방"));
        when(bookings.lockManagerBooking(manager, b.getId())).thenReturn(b);
    }
    CleaningPhoto photo(String room, boolean after) {
        CleaningPhoto p = CleaningPhoto.of(UUID.randomUUID(), b.getId(), room, 1, "before.jpg", NOW);
        if (after) p.replaceAfter("after.jpg", NOW);
        return p;
    }
    @Test void 전_사진_하나라도_누락되면_송출_확인_불가() {
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(photo("거실", false)));
        assertThatThrownBy(() -> service.confirmBefore(manager, b.getId())).isInstanceOf(ApiException.class);
        assertThat(b.getBeforePhotosConfirmedAt()).isNull();
    }
    @Test void 모든_전_사진_확인_후_영상_종료까지_멱등() {
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(photo("거실", false), photo("안방", false)));
        service.confirmBefore(manager, b.getId()); service.confirmBefore(manager, b.getId());
        assertThat(b.getPhotoPhase()).isEqualTo("live");
        assertThatThrownBy(() -> service.requireComplete(b)).isInstanceOf(ApiException.class);
        service.finishVideo(b); service.finishVideo(b);
        assertThat(b.getPhotoPhase()).isEqualTo("after");
        assertThatThrownBy(() -> service.confirmBefore(manager, b.getId())).isInstanceOf(ApiException.class);
    }
    @Test void 다른_공간_사진으로_필수_장소를_대신할_수_없다() {
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(photo("거실", true), photo("거실", true)));
        assertThatThrownBy(() -> service.confirmBefore(manager, b.getId())).isInstanceOf(ApiException.class);
    }
    @Test void 후_사진은_모든_공간과_추가_위치까지_있어야_완료() {
        b.confirmBeforePhotos(NOW); b.finishVideo(NOW);
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(photo("거실", true), photo("안방", false)));
        assertThatThrownBy(() -> service.requireComplete(b)).isInstanceOf(ApiException.class);
        when(photos.findByBookingIdOrderBySortAsc(b.getId())).thenReturn(List.of(photo("거실", true), photo("안방", true)));
        assertThatCode(() -> service.requireComplete(b)).doesNotThrowAnyException();
    }
}
