package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** /admin/** 의 X-Admin-Token 검사 (§13-20). 토큰이 비어 있으면 관리자 API 는 닫힌다 */
// 쓰는 곳: AdminTokenFilter(컨트롤러 앞)와 Admin*Controller(예: AdminOfferController — require 를 한 번 더 부른다).
// 비교할 토큰은 app.admin.token (환경변수 ADMIN_TOKEN).
@Component
public class AdminAuth {
    public static final String HEADER = "X-Admin-Token";

    private final String token;

    // 설정이 없으면 빈 문자열로 둔다 → require 가 항상 거절한다
    public AdminAuth(AppProperties props) {
        this.token = props.admin() == null || props.admin().token() == null ? "" : props.admin().token();
    }

    /**
     * 요청 헤더 값이 설정된 토큰과 같은지 검사한다. 설정 토큰이 비었거나, 헤더가 없거나, 다르면 401(UNAUTHENTICATED).
     * MessageDigest.isEqual: 어느 글자에서 다른지와 상관없이 비교 시간이 같은 비교 — 응답 시간으로 토큰을 짐작하기 어렵다.
     */
    public void require(String header) {
        if (token.isBlank() || header == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "관리자 토큰이 올바르지 않아요");
        }
    }
}
