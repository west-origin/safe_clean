package kr.ansimclean.api.booking.dto;

import java.util.List;

// 매니저 앱 GET /manager/jobs/{id}/photos 응답. photos = 찍은 순서, max = 예약 한 건에 찍을 수 있는 위치 수(10).
/** docs/api.md "청소 전·후 사진" — { photos: CleaningPhoto[], max } (앱 getJobPhotos) */
public record JobPhotosResponse(List<CleaningPhotoResponse> photos, int max, List<String> requiredRooms, String phase) {
    public JobPhotosResponse(List<CleaningPhotoResponse> photos, int max) { this(photos, max, List.of(), "before"); }
}
