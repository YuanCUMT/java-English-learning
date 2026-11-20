package com.dailyreading.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import static org.junit.jupiter.api.Assertions.*;

class CsrfProtectionTest {
    @Test void expiredBrowserFormShowsRecoveryWithoutEchoingInput() throws Exception {
        var req=new MockHttpServletRequest("POST","/register");
        req.addHeader("Accept","text/html,application/xhtml+xml");
        req.addParameter("password","do-not-echo-this");
        var res=new MockHttpServletResponse();var chain=new MockFilterChain();
        new CsrfProtection().doFilter(req,res,chain);
        assertEquals(403,res.getStatus());assertTrue(res.getContentType().startsWith("text/html"));
        assertTrue(res.getContentAsString().contains("href=\"/register\""));
        assertTrue(res.getContentAsString().contains("这次操作没有执行"));
        assertFalse(res.getContentAsString().contains("do-not-echo-this"));assertNull(chain.getRequest());
    }
    @Test void apiKeepsJsonContractEvenWhenHtmlIsAccepted() throws Exception {
        var req=new MockHttpServletRequest("POST","/api/words");req.addHeader("Accept","text/html");
        var res=new MockHttpServletResponse();var chain=new MockFilterChain();
        new CsrfProtection().doFilter(req,res,chain);
        assertEquals(403,res.getStatus());assertTrue(res.getContentType().startsWith("application/json"));
        assertTrue(res.getContentAsString().contains("\"code\":403"));assertNull(chain.getRequest());
    }
}
