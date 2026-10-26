package kr.ansimclean.api.booking.dto;

import java.util.List;

// 고객 앱 GET /customer/bookings/{id}/photos 응답.
// ready = 청소가 끝났는가(done). 끝나기 전에는 photos 가 항상 빈 목록이고, 끝난 뒤에는 전·후가 다 있는 위치만 담는다.
/** docs/api.md "청소 전·후 사진" — { ready, photos: CleaningPhoto[] } (앱 getCleaningPhotos) */
public record CustomerCleaningPhotosResponse(boolean ready, List<CleaningPhotoResponse> photos) {}
