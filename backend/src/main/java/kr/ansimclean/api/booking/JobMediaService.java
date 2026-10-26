package kr.ansimclean.api.booking;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/** 예약 잠금 아래 검사한다. 앱 경로를 직접 열거나 API를 호출해도 필수 촬영을 건너뛸 수 없다. */
@Service
@Transactional
public class JobMediaService {
    private final BookingService bookings;
    private final CleaningPhotoRepository photos;
    private final AppClock clock;
    public JobMediaService(BookingService bookings, CleaningPhotoRepository photos, AppClock clock) {
        this.bookings = bookings; this.photos = photos; this.clock = clock;
    }

    public void confirmBefore(UUID managerId, UUID id) {
        Booking b = bookings.lockManagerBooking(managerId, id);
        requireActive(b);
        if (b.getVideoFinishedAt() != null) throw ApiException.validation("이미 영상 촬영이 끝났어요");
        requirePhotos(b, false);
        b.confirmBeforePhotos(clock.now());
    }

    public void finishVideo(Booking b) {
        requireActive(b);
        if (b.getBeforePhotosConfirmedAt() == null) throw ApiException.validation("각 공간의 청소 전 사진을 먼저 찍어주세요");
        b.finishVideo(clock.now());
    }

    public void requireComplete(Booking b) {
        if (b.getVideoFinishedAt() == null) throw ApiException.validation("실시간 영상을 먼저 종료해주세요");
        requirePhotos(b, true);
    }

    private void requirePhotos(Booking b, boolean after) {
        List<CleaningPhoto> rows = photos.findByBookingIdOrderBySortAsc(b.getId());
        if (b.getRequiredPhotoRooms().isEmpty() || b.getRequiredPhotoRooms().stream().anyMatch(room ->
                rows.stream().noneMatch(p -> room.equals(p.getLocation()) && (!after || p.isPaired())))
                || (after && rows.stream().anyMatch(p -> !p.isPaired()))) {
            throw ApiException.validation(after ? "모든 공간의 청소 후 사진을 찍어주세요" : "모든 공간의 청소 전 사진을 찍어주세요");
        }
    }

    private static void requireActive(Booking b) {
        if (!BookingStatus.ACTIVE.contains(b.getStatus())) throw new ApiException(ErrorCode.INVALID_STATE, "진행 중인 작업에서만 사용할 수 있어요");
    }
}
