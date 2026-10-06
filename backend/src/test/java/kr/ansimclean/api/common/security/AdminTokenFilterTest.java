package kr.ansimclean.api.common.security;

import kr.ansimclean.api.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** /admin/** 는 컨트롤러에 닿기 전에 토큰이 검사된다 — 컨트롤러가 require 를 빼먹어도 열리지 않는다 (보안 검토) */
// 스프링을 띄우지 않고 필터를 직접 만들어 doFilter 를 부른다. MockHttpServletRequest/Response 는 가짜 HTTP 요청·응답,
// MockFilterChain 은 다음 단계(컨트롤러 쪽) 자리 — 필터가 요청을 넘겼으면 chain.getRequest() 가 null 이 아니다.
class AdminTokenFilterTest {
    // 관리자 토큰 "s3cret"(테스트용 가짜 값)이 설정된 필터
    AdminTokenFilter filter = new AdminTokenFilter(provider(new AdminAuth(new AppProperties(null, null, null, null, new AppProperties.Admin("s3cret")))));

    /** 필터 생성자가 받는 ObjectProvider(빈이 '있으면 달라' 상자)를 만든다. a 가 null 이면 AdminAuth 빈이 없는 상태 */
    static org.springframework.beans.factory.ObjectProvider<AdminAuth> provider(AdminAuth a) {
        // DefaultListableBeanFactory: 스프링의 빈 보관소를 작게 직접 만든 것. 여기에 넣은 빈만 provider 가 돌려준다
        org.springframework.beans.factory.support.DefaultListableBeanFactory f = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        if (a != null) f.registerSingleton("adminAuth", a);
        return f.getBeanProvider(AdminAuth.class);
    }

    // 맞는 토큰을 보내도 AdminAuth 빈 자체가 없으면(비교할 기준이 없다) 401
    @Test
    void AdminAuth_빈이_없으면_닫힌다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/admin/anything");
        // 필터는 getRequestURI 로 관리자 경로인지 가린다 (생성자 인자와 같은 경로를 한 번 더 넣는다)
        req.setRequestURI("/api/v1/admin/anything");
        req.addHeader(AdminAuth.HEADER, "s3cret");
        MockHttpServletResponse res = new MockHttpServletResponse();
        new AdminTokenFilter(provider(null)).doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void 토큰이_없거나_틀리면_401_JSON_이고_체인을_타지_않는다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/admin/anything");
        req.setRequestURI("/api/v1/admin/anything");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("UNAUTHENTICATED");
        assertThat(chain.getRequest()).isNull(); // 다음 단계로 넘어가지 않았다 = 컨트롤러에 닿지 않는다
    }

    @Test
    void 맞는_토큰이면_통과하고_admin_밖_경로는_건드리지_않는다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/admin/anything");
        req.setRequestURI("/api/v1/admin/anything");
        req.addHeader(AdminAuth.HEADER, "s3cret");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        // 관리자 경로가 아닌 요청은 토큰 없이도 그대로 다음 단계로 넘어간다
        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/v1/customer/home");
        other.setRequestURI("/api/v1/customer/home");
        MockFilterChain chain2 = new MockFilterChain();
        filter.doFilter(other, new MockHttpServletResponse(), chain2);
        assertThat(chain2.getRequest()).isNotNull(); // 토큰 없이도 통과 (검사 대상 아님)
    }
}
