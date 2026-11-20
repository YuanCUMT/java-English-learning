package com.dailyreading.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dailyreading.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Order(1)
public class CsrfProtection extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "csrfToken";
    public static String token(HttpServletRequest request) {
        HttpSession session = request.getSession();
        synchronized (session) {
            String value=(String)session.getAttribute(ATTRIBUTE);
            if(value==null) { value=UUID.randomUUID().toString(); session.setAttribute(ATTRIBUTE,value); }
            return value;
        }
    }
    private static String expectedToken(HttpServletRequest request) {
        return (String) request.getSession(false).getAttribute(ATTRIBUTE);
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws ServletException,IOException {
        if (Set.of("GET","HEAD","OPTIONS").contains(req.getMethod())) {
            // Static resources and health checks must not create sessions.
            if (!req.getRequestURI().matches("/(css|js|img)/.*|/favicon.ico|/api/health"))
                req.setAttribute(ATTRIBUTE,token(req));
        } else {
            HttpSession session=req.getSession(false);
            String expected=session==null?null:(String)session.getAttribute(ATTRIBUTE);
            String actual=req.getHeader("X-CSRF-TOKEN");
            if(actual==null && req.getContentType()!=null && req.getContentType().startsWith("application/x-www-form-urlencoded")) actual=req.getParameter("_csrf");
            if(expected==null || actual==null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(403);
                // Browser form submissions receive a recovery page; API callers keep the JSON contract.
                String accept=req.getHeader("Accept");
                if (!req.getRequestURI().startsWith("/api/") && accept!=null && accept.contains("text/html")) {
                    String recovery=Set.of("/login","/register").contains(req.getRequestURI())?req.getRequestURI():"/";
                    res.setContentType("text/html;charset=UTF-8");
                    res.getWriter().write("""
                        <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <title>页面已过期 · 每日阅读</title><link rel="stylesheet" href="/css/app.css">
                        <link rel="stylesheet" href="/css/refinements.css?v=20261115"><script src="/js/theme.js"></script>
                        </head><body class="auth-page"><main class="auth"><div class="eyebrow">Daily Reading</div>
                        <h1>重新打开页面后继续</h1><p class="sub">页面验证已过期，或当前页面来自旧的登录会话。</p>
                        <div class="alert" role="alert">这次操作没有执行。重新打开页面后，请检查输入并再次提交。</div>
                        <div class="error-actions"><a class="btn" href="%s">重新打开页面</a>
                        <a class="btn ghost" href="/login">返回登录</a></div></main></body></html>
                        """.formatted(recovery));
                } else {
                    res.setContentType("application/json;charset=UTF-8");
                    new ObjectMapper().writeValue(res.getWriter(),ApiResponse.fail(403,"页面验证已过期，请刷新页面后重试"));
                }
                return;
            }
        }
        // A valid POST may render a form again after a business validation error.
        // Expose the existing session token on that response as well as on GET.
        if (!Set.of("GET","HEAD","OPTIONS").contains(req.getMethod()))
            req.setAttribute(ATTRIBUTE, expectedToken(req));
        chain.doFilter(req,res);
    }
}
