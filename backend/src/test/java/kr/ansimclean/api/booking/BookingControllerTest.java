package kr.ansimclean.api.booking;

import kr.ansimclean.api.booking.dto.BookingResponse;
import kr.ansimclean.api.booking.dto.CancelRequest;
import kr.ansimclean.api.booking.dto.QuoteResponse;
import kr.ansimclean.api.booking.dto.ReservationResponse;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 컨트롤러 슬라이스 테스트 (CODE_GUIDE.md 3-12): BookingController·RepairPhotoController 와 웹 계층만 띄우고, 서비스는 @MockitoBean 가짜로 바꿔 끼운다.
// @Import: 슬라이스에 자동으로 들어오지 않는 보안 설정·토큰 변환기·CORS·오류 응답 처리기를 넣는다.
// 여기서는 "요청 → 서비스 호출 인자" 와 "서비스 결과·오류 → 응답 JSON·상태 코드" 만 본다 (예약 규칙은 서비스 테스트·IT)
/** 예약 API 의 권한·검증·JSON 모양·Idempotency-Key 전달 (docs/api.md §5.2) */
@WebMvcTest({ BookingController.class, RepairPhotoController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class BookingControllerTest {
    // MockMvc: 서버를 띄우지 않고 HTTP 요청을 흉내 내 보내고 응답을 검사하는 도구
    @Autowired
    MockMvc mvc;

    @MockitoBean
    BookingService bookings;

    @MockitoBean
    BookingSeriesService series;

    @MockitoBean
    RepairPhotoService repairPhotos;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    /** token 을 가입을 마친 고객의 JWT 로 풀게 정하고 그 고객 id 를 돌려준다 (ManagerMvc.customer 와 같은 일) */
    UUID customer(String token) {
        UUID id = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "HS256").subject(id.toString()).claim("aud", List.of("authenticated"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(jwtDecoder.decode(token)).thenReturn(jwt);
        when(resolver.resolve(id)).thenReturn(Optional.of(new CurrentUser(id, Roles.CUSTOMER, true)));
        return id;
    }

    @Test
    void 견적은_로그인이_필요하고_응답에_perVisit_visits_totalLabel_이_있다() throws Exception {
        String body = "{\"category\":\"home\",\"frequency\":\"recurring\",\"date\":\"2026-11-02\",\"time\":\"10:00\",\"freeOptions\":[],\"paidOptions\":[]}";
        // Authorization 헤더 없이 → 401
        mvc.perform(post("/api/v1/customer/bookings/quote").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        // 토큰 "c" = 고객. 서비스가 돌려준 견적이 perVisit·visits·totalLabel·paymentNote·savingNote 이름으로 나가는지
        customer("c");
        when(bookings.quote(any())).thenReturn(new QuoteResponse(new QuoteResponse.PerVisit(80000, 0, 80000, "80,000원", "80,000원", null),
                List.of(new QuoteResponse.Visit("2026-11-02", "2026년 11월 2일 (월)")), 1, 80000, "80,000원", "결제는 청소 완료 후 진행돼요", "1회 청소보다 회당 20,000원 저렴해요"));
        mvc.perform(post("/api/v1/customer/bookings/quote").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perVisit.label").value("80,000원"))
                .andExpect(jsonPath("$.visits[0].label").value("2026년 11월 2일 (월)"))
                .andExpect(jsonPath("$.totalLabel").value("80,000원"))
                .andExpect(jsonPath("$.paymentNote").value("결제는 청소 완료 후 진행돼요"))
                .andExpect(jsonPath("$.savingNote").value("1회 청소보다 회당 20,000원 저렴해요")); // 정기 할인을 화면이 말해 준다 (사용성 검토)
    }

    @Test
    void 생성은_Idempotency_Key_를_서비스에_넘기고_빈_주소는_400() throws Exception {
        UUID id = customer("c");
        UUID bookingId = UUID.randomUUID();
        // eq("k-1"): 헤더의 멱등 키가 두 번째 인자로 넘어왔을 때만 이 응답을 준다 — 안 넘어오면 mock 이 null 을 돌려줘 아래 확인이 실패한다
        when(bookings.create(eq(id), eq("k-1"), any())).thenReturn(new BookingResponse(bookingId, "home", "oneTime", "2026-11-02", "10:00", "주소", null,
                "가정/원룸 청소", "배정 후 알려드려요", false, false, false, null, List.of(new BookingResponse.Visit(bookingId, "2026-11-02", "2026년 11월 2일 (월)")), "80,000원", 80000, "80,000원"));
        String body = "{\"category\":\"home\",\"frequency\":\"oneTime\",\"date\":\"2026-11-02\",\"time\":\"10:00\",\"roadAddress\":\"주소\",\"freeOptions\":[],\"paidOptions\":[],\"notices\":[]}";
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", "Bearer c").header("Idempotency-Key", "k-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.cleaner").value("배정 후 알려드려요"))
                .andExpect(jsonPath("$.managerAssigned").value(false))
                .andExpect(jsonPath("$.matchingFailed").value(false)) // 수리 완료 화면이 '배정 중' 과 서버 실패 안내 중 무엇을 보일지 정한다 (문제 목록 8번)
                .andExpect(jsonPath("$.cancelled").value(false)) // 취소된 예약이면 true — 수리 완료 화면이 '배정 중' 대신 cleaner 를 보인다 (합친 38건 검토 R16)
                .andExpect(jsonPath("$.visits[0].id").value(bookingId.toString()));

        // roadAddress 가 빈 문자열 → 서비스까지 가지 않고 입력 검증 400 + 안내 문구
        mvc.perform(post("/api/v1/customer/bookings").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"home\",\"date\":\"2026-11-02\",\"time\":\"10:00\",\"roadAddress\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value("방문할 주소를 입력해주세요"));
    }

    @Test
    void 도메인_오류는_ErrorCode_의_상태로_나간다() throws Exception {
        UUID id = customer("c");
        UUID bookingId = UUID.randomUUID();
        // 기한 초과 문구는 Booking.cancel 이 설정된 기한 시간을 넣어 만든다 (문제 목록 25번) — 여기서는 그 문구를 그대로 던진다
        when(bookings.cancel(eq(id), eq(bookingId), any())).thenThrow(new ApiException(ErrorCode.BOOKING_NOT_CANCELLABLE, "방문 24시간 전까지만 취소할 수 있어요"));
        // 본문 없이 취소 (취소 본문은 선택) → 서비스가 던진 오류가 409 + 코드 + 한국어 문구로 나간다
        mvc.perform(post("/api/v1/customer/reservations/" + bookingId + "/cancel").header("Authorization", "Bearer c"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_CANCELLABLE"))
                .andExpect(jsonPath("$.error.message").value("방문 24시간 전까지만 취소할 수 있어요"));
    }

    @Test
    void wholeSeries_면_시리즈_서비스로_간다() throws Exception {
        UUID id = customer("c");
        UUID bookingId = UUID.randomUUID();
        when(series.cancelWhole(eq(id), eq(bookingId), any())).thenReturn(new ReservationResponse(bookingId, "cancelled", "home", "가정/원룸 청소", "cancelled",
                "2026-11-02", "10:00", true, false, false, List.of()));
        mvc.perform(post("/api/v1/customer/reservations/" + bookingId + "/cancel").header("Authorization", "Bearer c")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wholeSeries\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series").value(true));
        // ArgumentCaptor: 서비스에 넘어간 CancelRequest 를 붙잡아 wholeSeries 가 true 로 전달됐는지 꺼내 본다
        ArgumentCaptor<CancelRequest> req = ArgumentCaptor.forClass(CancelRequest.class);
        verify(series).cancelWhole(eq(id), eq(bookingId), req.capture());
        assertThat(req.getValue().wholeSeries()).isTrue();
    }

    @Test
    void 일정_변경은_형식을_검사하고_목록은_reservations_배열() throws Exception {
        UUID id = customer("c");
        UUID bookingId = UUID.randomUUID();
        // 날짜가 "YYYY-MM-DD" 가 아니면("11/2") 400. 목록은 비어 있어도 reservations 가 배열로 나간다
        mvc.perform(patch("/api/v1/customer/reservations/" + bookingId + "/schedule").header("Authorization", "Bearer c")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"11/2\",\"time\":\"10:00\"}"))
                .andExpect(status().isBadRequest());
        when(bookings.reservations(id)).thenReturn(new kr.ansimclean.api.booking.dto.ReservationsResponse(null, null, List.of(), 24));
        mvc.perform(get("/api/v1/customer/reservations").header("Authorization", "Bearer c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").isArray())
                .andExpect(jsonPath("$.changeDeadlineHours").value(24)); // 잠긴 카드·취소 시트 문구의 기한 시간 (문제 목록 25번)
    }

    @Test
    void 수리_사진은_고객만_multipart_로_올리고_경로와_URL_을_받는다() throws Exception {
        UUID id = customer("c");
        when(repairPhotos.upload(eq(id), eq("image/jpeg"), any())).thenReturn(new kr.ansimclean.api.booking.dto.RepairPhotoResponse(id + "/x.jpg", "https://signed"));
        // MockMultipartFile: 파일 업로드(multipart) 요청에 실을 가짜 파일 파트 — 파트 이름 "file", 형식 image/jpeg, 내용 2바이트.
        // 차례로: 고객 + 파일 → 200 / 파일 파트 없음 → 400 / 토큰 없음 → 401
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[] { 1, 2 });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/customer/repair-photos").file(file).header("Authorization", "Bearer c"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.path").value(id + "/x.jpg")).andExpect(jsonPath("$.url").value("https://signed"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/customer/repair-photos").header("Authorization", "Bearer c"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION")); // file 파트 없음
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/customer/repair-photos").file(file)).andExpect(status().isUnauthorized());
    }
}
