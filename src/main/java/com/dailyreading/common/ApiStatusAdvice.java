package com.dailyreading.common;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Keep JSON envelope while giving errors their real HTTP status. */
@RestControllerAdvice
public class ApiStatusAdvice implements ResponseBodyAdvice<Object> {
    public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> c) { return true; }
    public Object beforeBodyWrite(Object body,MethodParameter p,MediaType t,Class<? extends HttpMessageConverter<?>> c,ServerHttpRequest req,ServerHttpResponse res) {
        Integer errorCode=body instanceof ApiResponse r?r.getCode():body instanceof java.util.Map<?,?> m && m.get("code") instanceof Number n?n.intValue():0;
        if(errorCode!=0) {
            int code=errorCode;
            int status=code>=400 && code<=599?code:400;
            res.setStatusCode(HttpStatusCode.valueOf(status));
        }
        return body;
    }
}
