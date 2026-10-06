package kr.ansimclean.api.offer;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.offer.dto.AcceptResponse;
import kr.ansimclean.api.offer.dto.CallItem;
import kr.ansimclean.api.offer.dto.CallsResponse;
import kr.ansimclean.api.offer.dto.RejectResponse;
import kr.ansimclean.api.schedule.dto.AvailabilityStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 콜 API 의 권한·검증·JSON 모양·Idempotency-Key 전달 (docs/api.md §5.3) */
// 컨트롤러 슬라이스 테스트: OfferController 와 보안·오류 처리만 띄우고 OfferService 는 MockitoBean 가짜다 (DB 없음).
// ManagerMvc 가 토큰 문자열("Bearer m", "Bearer c")을 매니저·고객으로 풀리게 정해 준다.
// 확인: 고객은 403, 콜 목록 JSON 키, 수락 때 Idempotency-Key 헤더가 서비스로 그대로 가는지(없으면 null),
// OFFER_TAKEN 은 409 + 한국어 문구, 거절 사유가 비면 400
@WebMvcTest(OfferController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class OfferControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    OfferService service;

    // 토큰 해석과 '사용자 id → 역할' 조회도 가짜 — 테스트마다 ManagerMvc 가 무엇을 돌려줄지 정한다
    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 고객 토큰으로 /manager/** 를 부르면 403 (SecurityConfig 경로 권한). 매니저면 서비스가 준 콜 카드가 약속한 JSON 키로 나간다
    @Test
    void 콜_목록은_매니저만_보고_카드_모양으로_온다() throws Exception {
        ManagerMvc.customer(jwtDecoder, resolver, "c");
        mvc.perform(get("/api/v1/manager/offers").header("Authorization", "Bearer c")).andExpect(status().isForbidden());

        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID offerId = UUID.randomUUID();
        // CallItem 인자 순서: id, expiry, expiresAt, title, location, pay, eta(null), note, details.
        // CallsResponse(tabBadge 1, 카드들, 수신 상태 카드, approved true)
        when(service.calls(id)).thenReturn(new CallsResponse(1, List.of(new CallItem(offerId, "4:32 후 만료", "2026-09-15T05:04:32Z", "9월 18일 목요일 오후 2:00",
                "서울특별시 강남구 테헤란로 · 가정/원룸 청소", "85,000원", null, "특이사항: 반려동물이 있어요", List.of("예상 소요 3시간", "아파트·빌라 · 25평"))), new AvailabilityStatus("콜 수신 중", "오늘 09:00 - 18:00 근무 설정됨"), true));
        mvc.perform(get("/api/v1/manager/offers").header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabBadge").value(1))
                .andExpect(jsonPath("$.calls[0].id").value(offerId.toString()))
                .andExpect(jsonPath("$.calls[0].expiry").value("4:32 후 만료"))
                .andExpect(jsonPath("$.calls[0].note").value("특이사항: 반려동물이 있어요"))
                .andExpect(jsonPath("$.calls[0].details[0]").value("예상 소요 3시간")) // 수락 전에 보이는 정보 (사용성 검토)
                .andExpect(jsonPath("$.calls[0].eta").doesNotExist()) // doesNotExist 는 키가 없거나 값이 null 이면 통과한다
                .andExpect(jsonPath("$.availability.status").value("콜 수신 중"));
    }

    // 헤더 "k-1" 로 보내면 서비스가 정확히 그 키로 불려야 when(…) 조건에 맞아 jobId 가 나온다.
    // 헤더 없이 다른 콜(taken)을 수락하면 key = null 로 오고, 서비스가 OFFER_TAKEN 을 던지면 409 + ErrorCode 의 기본 한국어 문구
    @Test
    void 수락은_Idempotency_Key_를_넘기고_jobId_를_돌려준다_경합은_409() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID offerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(service.accept(id, offerId, "k-1")).thenReturn(new AcceptResponse(jobId));
        mvc.perform(post("/api/v1/manager/offers/" + offerId + "/accept").header("Authorization", "Bearer m").header("Idempotency-Key", "k-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(jobId.toString()));

        UUID taken = UUID.randomUUID();
        // eq(…) = 그 값과 같을 때 (Mockito 규칙: 인자 조건을 하나라도 쓰면 나머지 인자도 모두 조건으로 적는다)
        when(service.accept(eq(id), eq(taken), eq(null))).thenThrow(new ApiException(ErrorCode.OFFER_TAKEN));
        mvc.perform(post("/api/v1/manager/offers/" + taken + "/accept").header("Authorization", "Bearer m"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OFFER_TAKEN")).andExpect(jsonPath("$.error.message").value("다른 매니저가 먼저 수락했어요"));
    }

    // 사유가 빈 문자열이면 RejectRequest 의 NotBlank 검사에 걸려 서비스까지 가지 않고 400 + 검사 문구. 사유가 있으면 서비스 응답 그대로
    @Test
    void 거절은_사유가_필수() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID offerId = UUID.randomUUID();
        mvc.perform(post("/api/v1/manager/offers/" + offerId + "/reject").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.message").value("거절 사유를 골라주세요"));
        when(service.reject(id, offerId, "개인 사정")).thenReturn(new RejectResponse(offerId, "개인 사정"));
        mvc.perform(post("/api/v1/manager/offers/" + offerId + "/reject").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("개인 사정"));
    }

    // 닫힌 콜의 거절 오류는 409 + { error: { code, message } } — 앱은 OFFER_TAKEN·OFFER_EXPIRED 면 마감 시트, INVALID_STATE 면 문구를 보인다 (문제 목록 10번)
    @Test
    void 닫힌_콜_거절은_409_와_오류_코드() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID taken = UUID.randomUUID();
        when(service.reject(id, taken, "개인 사정")).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.OFFER_TAKEN));
        mvc.perform(post("/api/v1/manager/offers/" + taken + "/reject").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OFFER_TAKEN"));
        UUID mine = UUID.randomUUID();
        when(service.reject(id, mine, "개인 사정")).thenThrow(new kr.ansimclean.api.common.error.ApiException(kr.ansimclean.api.common.error.ErrorCode.INVALID_STATE, "이미 수락한 콜이에요. 일정에서 확인해주세요"));
        mvc.perform(post("/api/v1/manager/offers/" + mine + "/reject").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"개인 사정\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.error.message").value("이미 수락한 콜이에요. 일정에서 확인해주세요"));
    }
}
