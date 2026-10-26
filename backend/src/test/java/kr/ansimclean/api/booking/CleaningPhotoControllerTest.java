package kr.ansimclean.api.booking;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.booking.dto.CleaningPhotoResponse;
import kr.ansimclean.api.booking.dto.CustomerCleaningPhotosResponse;
import kr.ansimclean.api.booking.dto.JobPhotosResponse;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 청소 전·후 사진 컨트롤러 슬라이스(@WebMvcTest): JSON 모양이 docs/api.md 와 글자까지 같은지, 역할(매니저/고객)별 403·401, 오류 코드 전달.
 * 서비스는 목이다 — 검사 규칙은 CleaningPhotoServiceTest 가 본다.
 */
@WebMvcTest({ ManagerCleaningPhotoController.class, CustomerCleaningPhotoController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class CleaningPhotoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CleaningPhotoService service;
    @MockitoBean JobMediaService media;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean CurrentUserResolver resolver;

    static final MockMultipartFile FILE = new MockMultipartFile("file", "a.png", "image/png", RepairPhotoServiceTest.png(10, 10));

    @Test
    void 매니저_목록_모양() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID job = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        when(service.managerList(manager, job)).thenReturn(new JobPhotosResponse(List.of(new CleaningPhotoResponse(photo, "거실 소파 앞", "https://b", null)), 10));
        mvc.perform(get("/api/v1/manager/jobs/" + job + "/photos").header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max").value(10))
                .andExpect(jsonPath("$.photos[0].id").value(photo.toString()))
                .andExpect(jsonPath("$.photos[0].location").value("거실 소파 앞"))
                .andExpect(jsonPath("$.photos[0].beforeUrl").value("https://b"))
                .andExpect(jsonPath("$.photos[0].afterUrl").isEmpty()); // null 도 키는 내려간다
    }

    @Test
    void 전_사진_올리기는_file_과_location_을_넘긴다() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID job = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        when(service.addBefore(eq(manager), eq(job), eq("거실 소파 앞"), eq("image/png"), any())).thenReturn(new CleaningPhotoResponse(photo, "거실 소파 앞", "https://b", null));
        mvc.perform(multipart("/api/v1/manager/jobs/" + job + "/photos").file(FILE).param("location", "거실 소파 앞").header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(photo.toString()))
                .andExpect(jsonPath("$.afterUrl").isEmpty());
        // location 을 빠뜨리면 서비스가 null 을 받아 한국어 문구로 400 을 낸다
        when(service.addBefore(eq(manager), eq(job), eq(null), any(), any())).thenThrow(ApiException.validation(CleaningPhotoService.BAD_LOCATION));
        mvc.perform(multipart("/api/v1/manager/jobs/" + job + "/photos").file(FILE).header("Authorization", "Bearer m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value(CleaningPhotoService.BAD_LOCATION));
    }

    @Test
    void 후_사진은_PUT_multipart_진행_중이_아니면_409() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID job = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        when(service.addAfter(eq(manager), eq(job), eq(photo), eq("image/png"), any())).thenReturn(new CleaningPhotoResponse(photo, "거실", "https://b", "https://a"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/manager/jobs/" + job + "/photos/" + photo + "/after").file(FILE).header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afterUrl").value("https://a"));
        UUID done = UUID.randomUUID();
        when(service.addAfter(eq(manager), eq(done), eq(photo), any(), any())).thenThrow(new ApiException(ErrorCode.INVALID_STATE, CleaningPhotoService.NOT_ACTIVE));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/manager/jobs/" + done + "/photos/" + photo + "/after").file(FILE).header("Authorization", "Bearer m"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATE"));
    }

    @Test
    void 지우기는_204_없는_사진은_404() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID job = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        mvc.perform(delete("/api/v1/manager/jobs/" + job + "/photos/" + photo).header("Authorization", "Bearer m"))
                .andExpect(status().isNoContent());
        verify(service).delete(manager, job, photo);
        UUID missing = UUID.randomUUID();
        doThrow(ApiException.notFound()).when(service).delete(manager, job, missing);
        mvc.perform(delete("/api/v1/manager/jobs/" + job + "/photos/" + missing).header("Authorization", "Bearer m"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 매니저_경로는_매니저만() throws Exception {
        ManagerMvc.customer(jwtDecoder, resolver, "c");
        String path = "/api/v1/manager/jobs/" + UUID.randomUUID() + "/photos";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer c")).andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(FILE).param("location", "거실").header("Authorization", "Bearer c")).andExpect(status().isForbidden());
    }

    @Test
    void 고객_목록_모양과_남의_예약_404() throws Exception {
        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        UUID booking = UUID.randomUUID();
        UUID photo = UUID.randomUUID();
        when(service.customerList(customer, booking)).thenReturn(new CustomerCleaningPhotosResponse(true, List.of(new CleaningPhotoResponse(photo, "주방", "https://b", "https://a"))));
        mvc.perform(get("/api/v1/customer/bookings/" + booking + "/photos").header("Authorization", "Bearer c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.photos[0].id").value(photo.toString()))
                .andExpect(jsonPath("$.photos[0].location").value("주방"))
                .andExpect(jsonPath("$.photos[0].beforeUrl").value("https://b"))
                .andExpect(jsonPath("$.photos[0].afterUrl").value("https://a"));
        UUID notDone = UUID.randomUUID();
        when(service.customerList(customer, notDone)).thenReturn(new CustomerCleaningPhotosResponse(false, List.of()));
        mvc.perform(get("/api/v1/customer/bookings/" + notDone + "/photos").header("Authorization", "Bearer c"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false)).andExpect(jsonPath("$.photos").isEmpty());
        UUID other = UUID.randomUUID();
        when(service.customerList(customer, other)).thenThrow(ApiException.notFound());
        mvc.perform(get("/api/v1/customer/bookings/" + other + "/photos").header("Authorization", "Bearer c")).andExpect(status().isNotFound());
    }

    @Test
    void 고객_경로는_고객만() throws Exception {
        ManagerMvc.manager(jwtDecoder, resolver, "m");
        String path = "/api/v1/customer/bookings/" + UUID.randomUUID() + "/photos";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer m")).andExpect(status().isForbidden());
    }
}
