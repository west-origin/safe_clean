package kr.ansimclean.api.friend;

import kr.ansimclean.api.ManagerMvc;
import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.config.CorsConfig;
import kr.ansimclean.api.common.error.GlobalExceptionHandler;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUserResolver;
import kr.ansimclean.api.common.security.JwtToCurrentUserConverter;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.friend.dto.FriendEntry;
import kr.ansimclean.api.friend.dto.FriendListResponse;
import kr.ansimclean.api.friend.dto.FriendRequestResult;
import kr.ansimclean.api.friend.dto.FriendSearchResponse;
import kr.ansimclean.api.friend.dto.FriendUser;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /customer/friends/** 의 권한·JSON 모양, 공개 초대 페이지 /i/{code} (api.md '고객 친구'). 서비스는 가짜 */
@WebMvcTest({ FriendController.class, InvitePageController.class })
@Import({ SecurityConfig.class, JwtToCurrentUserConverter.class, CorsConfig.class, GlobalExceptionHandler.class, AccessPolicy.class })
@EnableConfigurationProperties(AppProperties.class)
@ActiveProfiles("test")
class FriendControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    FriendService service;
    @MockitoBean
    InviteProperties props;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CurrentUserResolver resolver;

    UUID customer;
    final UUID other = UUID.randomUUID();
    final FriendUser user = new FriendUser(other, "청소왕", "0420", "청소왕#0420", "김*수");

    @BeforeEach
    void stub() {
        customer = ManagerMvc.customer(jwtDecoder, resolver, "c");
        ManagerMvc.manager(jwtDecoder, resolver, "m");
        when(props.androidStoreUrl()).thenReturn("");
        when(props.iosStoreUrl()).thenReturn("");
    }

    @Test
    void 고객만_쓸_수_있다() throws Exception {
        mvc.perform(get("/api/v1/customer/friends")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/customer/friends").header("Authorization", "Bearer m")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/customer/friends/requests").header("Authorization", "Bearer m").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + other + "\"}")).andExpect(status().isForbidden());
    }

    @Test
    void 검색_목록_요청의_JSON_모양() throws Exception {
        when(service.search(customer, "청소왕#0420")).thenReturn(new FriendSearchResponse(user, "none"));
        mvc.perform(get("/api/v1/customer/friends/search").param("q", "청소왕#0420").header("Authorization", "Bearer c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value(other.toString()))
                .andExpect(jsonPath("$.user.handle").value("청소왕#0420"))
                .andExpect(jsonPath("$.user.maskedName").value("김*수"))
                .andExpect(jsonPath("$.user.phone").doesNotExist())
                .andExpect(jsonPath("$.relation").value("none"));

        when(service.friends(customer)).thenReturn(new FriendListResponse(List.of(new FriendEntry(user, null, "2026-11-13T03:00:00Z", "11월 13일"))));
        mvc.perform(get("/api/v1/customer/friends").header("Authorization", "Bearer c"))
                .andExpect(jsonPath("$.friends[0].user.nickname").value("청소왕"))
                .andExpect(jsonPath("$.friends[0].atLabel").value("11월 13일"));

        UUID req = UUID.randomUUID();
        when(service.sendRequest(eq(customer), eq("key-1"), eq(other))).thenReturn(new FriendRequestResult(req, "pending", user));
        mvc.perform(post("/api/v1/customer/friends/requests").header("Authorization", "Bearer c").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + other + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(req.toString()))
                .andExpect(jsonPath("$.status").value("pending"));

        mvc.perform(post("/api/v1/customer/friends/requests").header("Authorization", "Bearer c").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/customer/friends/requests/" + req + "/accept").header("Authorization", "Bearer c")).andExpect(status().isOk());
        verify(service).accept(customer, req);
    }

    @Test
    void 초대_페이지는_로그인_없이_열리고_만료면_404() throws Exception {
        when(service.inviterNickname("Abcdef2345")).thenReturn(Optional.of("청소왕"));
        mvc.perform(get("/i/Abcdef2345"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("청소왕님이 안심클린 친구로 초대했어요")))
                .andExpect(content().string(containsString("ansimclean://friend-invite/Abcdef2345")));

        when(service.inviterNickname(any())).thenReturn(Optional.empty());
        mvc.perform(get("/i/Zzzzzzzzzz")).andExpect(status().isNotFound()).andExpect(content().string(containsString("초대 링크가 만료됐어요")));
    }
}
