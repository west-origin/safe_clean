package kr.ansimclean.api.live;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.AdminAuth;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.live.dto.LiveStatusResponse;
import kr.ansimclean.api.live.dto.RegisterDeviceResponse;
import kr.ansimclean.api.live.dto.StartLiveResponse;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 라이브·웹훅·관리자 API 의 권한·오류 JSON (docs/api.md §5.4) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). 라이브·웹훅·운영자 기기 컨트롤러를 묶었고, 서비스·LiveKit 창구(gateway)·AdminAuth 는 가짜다.
// 확인하는 것: 경로별 로그인·역할 검사(401·403), 서비스가 던진 오류 코드가 HTTP 상태와 { error } JSON 으로 바뀌는지,
// 웹훅 서명 실패 401, 운영자 토큰 헤더와 본문 검증(400).
@WebMvcTest({ LiveController.class, LiveWebhookController.class, AdminDeviceController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class LiveControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    LiveService live;

    @MockitoBean
    LiveWebhookService webhooks;

    @MockitoBean
    DeviceService deviceService;

    @MockitoBean
    LiveKitGateway gateway;

    @MockitoBean
    AdminAuth adminAuth;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // 웹훅 경로는 로그인 없이 들어온다(SecurityConfig permitAll). 서명 검사는 gateway.parseWebhook 이 하는데 여기서는 가짜라,
    // Authorization 값 "bad" 면 401 오류를 던지고 "good" 이면 이벤트를 돌려주게 꾸민다 → 401 JSON / 204 + 서비스에 그 이벤트가 넘어갔는지
    @Test
    void 웹훅은_서명이_틀리면_401_맞으면_204_로_처리한다() throws Exception {
        when(gateway.parseWebhook(anyString(), eq("bad"))).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED, "웹훅 서명이 올바르지 않아요"));
        mvc.perform(post("/api/v1/live/webhooks/livekit").header("Authorization", "bad").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        LiveKitGateway.WebhookEvent event = new LiveKitGateway.WebhookEvent("e1", "room_finished", "booking-x", null, null, null);
        when(gateway.parseWebhook(anyString(), eq("good"))).thenReturn(event);
        mvc.perform(post("/api/v1/live/webhooks/livekit").header("Authorization", "good").contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"room_finished\"}"))
                .andExpect(status().isNoContent());
        verify(webhooks).handle(event);
    }

    // /live/** 는 로그인만 보고 역할은 보지 않는다(소유권은 서비스가 본다) → 매니저 토큰으로 시작 200, 토큰 없으면 401.
    // /customer/** 는 고객 역할만 → 매니저 토큰은 403.
    // 이어서 고객 토큰으로 서비스 오류가 상태로 바뀌는지 본다: LIVE_NOT_AVAILABLE → 409, LIVE_PAUSE_UNCONFIRMED → 502
    // (pause 를 고객이 부르는 것은 오류 변환만 보려는 것이다 — 가짜 서비스가 누가 불러도 그 오류를 던진다)
    @Test
    void 라이브_시작은_로그인만_요구하고_고객_라이브_화면은_고객만() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID id = UUID.randomUUID();
        when(live.start(manager, id)).thenReturn(new StartLiveResponse("booking-" + id, "waiting_camera", false, true, new StartLiveResponse.Preview("wss://lk", "tok")));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/start").header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cameraBusy").value(true)).andExpect(jsonPath("$.preview.token").value("tok"));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/start")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/bookings/" + id + "/live").header("Authorization", "Bearer m")).andExpect(status().isForbidden());

        UUID customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        when(live.viewerToken(customer, id)).thenThrow(new ApiException(ErrorCode.LIVE_NOT_AVAILABLE));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/token").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"viewer\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LIVE_NOT_AVAILABLE"));
        when(live.pause(customer, id)).thenThrow(new ApiException(ErrorCode.LIVE_PAUSE_UNCONFIRMED));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/pause").header("Authorization", "Bearer c")).andExpect(status().isBadGateway());
    }

    // 휴대폰 송출 모드(§8.0)의 JSON 모양: start 는 preview 가 null 이고 publish{url,token}, 상태는 source='app'. 매니저 토큰만 받는다(고객 경로와 분리)
    @Test
    void 휴대폰_송출_모드의_시작_응답은_publish_상태_응답은_source() throws Exception {
        UUID manager = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID id = UUID.randomUUID();
        when(live.start(manager, id)).thenReturn(new StartLiveResponse("booking-" + id, "waiting_camera", false, false, null, new StartLiveResponse.Publish("wss://lk", "pub")));
        mvc.perform(post("/api/v1/live/sessions/" + id + "/start").header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preview").isEmpty())
                .andExpect(jsonPath("$.publish.url").value("wss://lk")).andExpect(jsonPath("$.publish.token").value("pub"));
        when(live.status(manager, id)).thenReturn(new LiveStatusResponse("live", true, false, false, 1, null, false, "app", "failed", true));
        mvc.perform(get("/api/v1/live/sessions/" + id).header("Authorization", "Bearer m"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("app")).andExpect(jsonPath("$.cameraOnline").value(true))
                .andExpect(jsonPath("$.recordingStatus").value("failed")).andExpect(jsonPath("$.recordingStartFailed").value(true));
    }

    // 가짜 AdminAuth 는 헤더가 없을 때(require(null))만 401 을 던진다 → 헤더 없이 401, 헤더가 있으면 200 과 전체 스트림 키.
    // 마지막은 헤더는 있지만 본문에 managerId·serial 이 없어 컨트롤러의 검증기(Validator)가 400 을 낸다
    @Test
    void 관리자_기기_등록은_토큰_헤더로만() throws Exception {
        UUID manager = UUID.randomUUID();
        doThrow(new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요")).when(adminAuth).require(isNull());
        mvc.perform(post("/api/v1/admin/devices").contentType(MediaType.APPLICATION_JSON).content("{\"managerId\":\"" + manager + "\",\"name\":\"안심캠\",\"serial\":\"AC-1\"}"))
                .andExpect(status().isUnauthorized());
        when(deviceService.register(any())).thenReturn(new RegisterDeviceResponse(manager, "안심캠", "AC-1", "ing-1", "rtmp://lk/x", "full-key"));
        mvc.perform(post("/api/v1/admin/devices").header(AdminAuth.HEADER, "t").contentType(MediaType.APPLICATION_JSON).content("{\"managerId\":\"" + manager + "\",\"name\":\"안심캠\",\"serial\":\"AC-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.streamKey").value("full-key"));
        mvc.perform(post("/api/v1/admin/devices").header(AdminAuth.HEADER, "t").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"안심캠\"}"))
                .andExpect(status().isBadRequest());
    }
}
