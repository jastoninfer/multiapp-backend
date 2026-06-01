package com.example.multiapp.testinfra;

import com.example.multiapp.common.tenant.RequestContext;
import com.example.multiapp.membership.model.MembershipRole;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

// 用header构造RequestContext并注入controller参数
// 测试时需要再http request的header增加X-User-Id, X-Role等
// (实际上真的需要吗?, 我们通过filter/interceptor时也会自动构建这些内容)
// 但是由于我们不集成keycloak, 拿不到sub和issuer, 所以可能需要手动指定
// X-User-Id(理论上来说, X-Role应该是冗余信息)
@TestConfiguration
public class TestRequestContextWebConfig {
    public static final String ATTR = "ctx";

    @Bean
    OncePerRequestFilter testRequestContextFilter() {
        return new OncePerRequestFilter() {
            @Override
           protected void doFilterInternal(@NonNull HttpServletRequest req,
                                           @NonNull HttpServletResponse resp,
                                           @NonNull FilterChain chain)
            throws ServletException, IOException {
                String tenant = req.getHeader("X-Tenant-Id");
                String user = req.getHeader("X-User-Id");
                String role = req.getHeader("X-Role");
                String reqId = req.getHeader("X-Request-Id");
                if(StringUtils.isBlank(tenant))
                    tenant = "00000000-0000-0000-0000-000000000001";
                if(StringUtils.isBlank(user))
                    user = "00000000-0000-0000-0000-000000000101";
                if(StringUtils.isBlank(role)) role = "AGENT";
                if(StringUtils.isBlank(reqId)) reqId = "it-" + System.nanoTime();

                RequestContext ctx = new RequestContext(
                        UUID.fromString(tenant),
                        UUID.fromString(user),
                        false,
                        MembershipRole.valueOf(role),
                        "test-issuer",
                        "test-sub",
                        reqId
                );
                req.setAttribute(ATTR, ctx);
                // 构造假的JWT principal到SecurityContext

                chain.doFilter(req, resp);
            }
        };
    }

    @Bean
    WebMvcConfigurer testRequestContextResolverConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(@NonNull MethodParameter parameter) {
                        return parameter.getParameterType().equals(RequestContext.class);
                    }

                    @Override
                    public Object resolveArgument(@NonNull MethodParameter parameter,
                                                  @Nullable ModelAndViewContainer mavContainer,
                                                  @NonNull NativeWebRequest webRequest,
                                                  @Nullable WebDataBinderFactory binderFactory)
                            throws Exception {
                        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
                        Object obj = req.getAttribute(ATTR);
                        if(obj == null) throw new IllegalStateException("Missing RequestContext");
                        return obj;
                    }
                });
            }
        };
    }
}
