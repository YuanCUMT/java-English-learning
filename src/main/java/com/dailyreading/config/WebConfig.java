package com.dailyreading.config;

import com.dailyreading.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 登录拦截：除登录/注册/静态资源外，其余页面都要求有会话。
 * 用拦截器而不是 Spring Security，结课项目里更好讲清楚「怎么做到的」。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/register", "/logout",
                        "/css/**", "/js/**", "/img/**", "/favicon.ico",
                        "/api/health", "/error"
                );
    }

    static class LoginInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute(AuthController.SESSION_USER_ID) != null) {
                return true;
            }
            // API 请求返回 401，页面请求跳登录
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"请先登录\",\"data\":null}");
                return false;
            }
            response.sendRedirect("/login");
            return false;
        }
    }
}
