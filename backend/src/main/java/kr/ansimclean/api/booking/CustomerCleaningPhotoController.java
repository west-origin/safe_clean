package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.CustomerCleaningPhotosResponse;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 고객 앱의 청소 전·후 사진 보기: GET /api/v1/customer/bookings/{id}/photos (고객 앱 getCleaningPhotos).
// /customer/** 는 SecurityConfig 가 고객 역할만 들여보내고, 자기 예약인지는 CleaningPhotoService 가 본다(남의 예약 404).
// 청소가 끝나기 전에는 { ready: false, photos: [] } 이다.
/** docs/api.md "청소 전·후 사진 추가 (2026-10-23)" — 고객 1개 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/bookings/{id}/photos")
public class CustomerCleaningPhotoController {
    private final CleaningPhotoService service;

    public CustomerCleaningPhotoController(CleaningPhotoService service) {
        this.service = service;
    }

    /** { ready, photos } — ready 가 true 일 때만 전·후가 다 있는 위치가 담긴다 */
    @GetMapping
    public CustomerCleaningPhotosResponse list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.customerList(user.id(), id);
    }
}
