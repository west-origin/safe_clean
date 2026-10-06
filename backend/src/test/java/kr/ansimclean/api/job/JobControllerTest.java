package kr.ansimclean.api.job;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.ActiveJobResponse;
import kr.ansimclean.api.job.dto.ChecklistResponse;
import kr.ansimclean.api.job.dto.JobIdResponse;
import kr.ansimclean.api.job.dto.StartResponse;
import kr.ansimclean.api.live.dto.DeviceStatusRow;
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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 작업 API 의 경로 우선순위·검증·오류 JSON (docs/api.md §5.3) */
// 컨트롤러 슬라이스(CODE_GUIDE.md 3-12). JobService 는 가짜라 서비스 규칙은 JobServiceTest 가 본다.
// 여기서는 경로가 맞는 메서드로 가는지, 헤더·본문이 서비스에 그대로 넘어가는지, 본문 검증(400)과 서비스 오류가 { error: { code, message } } JSON 으로 나가는지를 본다. 토큰 "m" 은 ManagerMvc 가 매니저로 꾸민다.
@WebMvcTest(JobController.class)
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class JobControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    JobService service;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    CurrentUserResolver resolver;

    // "/active" 는 "/{id}" 에도 맞을 수 있는 경로다. 고정 경로가 먼저 맞아 activeJob 이 불리는지 본다
    // (가짜 서비스의 activeJob 에 꾸며 둔 결과가 응답에 나오면 그 메서드로 간 것이다).
    // 1) 진행 중 작업이 없으면(서비스가 NOT_FOUND) 404 JSON → 2) 있으면 체크리스트 묶음·장비 행의 JSON 칸 이름
    @Test
    void active_는_id_경로보다_먼저_맞고_없으면_404_JSON() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        when(service.activeJob(id)).thenThrow(ApiException.notFound());
        mvc.perform(get("/api/v1/manager/jobs/active").header("Authorization", "Bearer m"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        UUID jobId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        // 위에서 예외를 던지게 꾸민 메서드를 다시 꾸미므로 doReturn(…).when(service).activeJob(id) 모양을 쓴다.
        // when(service.activeJob(id)) 모양은 꾸미는 도중에 그 메서드를 실제로 불러 앞의 예외가 터진다
        doReturn(new ActiveJobResponse(jobId, "inProgress", "청소 진행 중", "00:10:00", "2026-09-15T05:00:00Z", 0, "14:00부터 진행 · 실시간 촬영 중", "14:00부터 진행", true,
                List.of(DeviceStatusRow.ok("카메라", "정상")), List.of("반려동물이 있어요"),
                List.of(new ActiveJobResponse.Group("거실", List.of(new ActiveJobResponse.Item(itemId, "바닥 청소", false)))))).when(service).activeJob(id);
        mvc.perform(get("/api/v1/manager/jobs/active").header("Authorization", "Bearer m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.checklist[0].group").value("거실"))
                .andExpect(jsonPath("$.checklist[0].items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.equipment[0].status").value("ok"));
    }

    // 가짜 서비스는 정확히 그 인자(키 "k"·"k2")로 불릴 때만 응답을 돌려주게 꾸민다 → 응답 JSON 이 나오면 헤더 값이 서비스까지 넘어간 것이다.
    // 일시정지: 본문 {} 는 @NotBlank 검증으로 400(VALIDATION). 사유가 있으면 서비스까지 가고,
    // 서비스가 INVALID_STATE("영상을 먼저 중단해주세요")를 던지면 409 와 그 문구가 JSON 으로 나간다
    @Test
    void 시작과_완료는_Idempotency_Key_를_넘기고_일시정지는_사유_검증과_선행_조건_오류를_JSON_으로() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID jobId = UUID.randomUUID();
        when(service.start(id, jobId, "k")).thenReturn(new StartResponse(jobId, "2026-09-15T05:00:00Z"));
        mvc.perform(post("/api/v1/manager/jobs/" + jobId + "/start").header("Authorization", "Bearer m").header("Idempotency-Key", "k"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startedAt").value("2026-09-15T05:00:00Z"));
        when(service.complete(id, jobId, "k2")).thenReturn(new JobIdResponse(jobId));
        mvc.perform(post("/api/v1/manager/jobs/" + jobId + "/complete").header("Authorization", "Bearer m").header("Idempotency-Key", "k2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jobId").value(jobId.toString()));

        mvc.perform(post("/api/v1/manager/jobs/" + jobId + "/pause").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        when(service.pause(id, jobId, "고객 요청")).thenThrow(new ApiException(ErrorCode.INVALID_STATE, "영상을 먼저 중단해주세요"));
        mvc.perform(post("/api/v1/manager/jobs/" + jobId + "/pause").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"고객 요청\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.message").value("영상을 먼저 중단해주세요"));
    }

    // 본문에 done 이 없으면 ChecklistRequest 의 @NotNull 이 400 으로 막고, { "done": true } 면 서비스 응답이 그대로 나간다
    @Test
    void 체크리스트는_done_이_필수() throws Exception {
        UUID id = ManagerMvc.manager(jwtDecoder, resolver, "m");
        UUID jobId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        mvc.perform(put("/api/v1/manager/jobs/" + jobId + "/checklist/" + itemId).header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        when(service.setChecklistItem(id, jobId, itemId, true)).thenReturn(new ChecklistResponse(jobId, itemId, true));
        mvc.perform(put("/api/v1/manager/jobs/" + jobId + "/checklist/" + itemId).header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON).content("{\"done\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
    }
}
