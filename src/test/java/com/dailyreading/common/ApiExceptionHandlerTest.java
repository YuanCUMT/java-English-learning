package com.dailyreading.common;

import org.junit.jupiter.api.Test;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 415 这条是踩过坑才补的：前端漏了 Content-Type，原来会把异常类名直接抛给用户看。
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void 格式不对时给出能看懂的提示() {
        ApiResponse r = handler.mediaType(new HttpMediaTypeNotSupportedException("不支持的请求类型"));
        assertEquals(415, r.getCode());
        assertTrue(r.getMessage().contains("application/json"), r.getMessage());
        assertFalse(r.getMessage().contains("Exception"), "不要把异常类名抛给用户");
    }

    @Test
    void 参数错误是400() {
        assertEquals(400, handler.badRequest(new IllegalArgumentException("单词为空")).getCode());
    }

    @Test
    void 未预期异常是500且不暴露细节() {
        ApiResponse r = handler.unexpected(new RuntimeException("db down"));
        assertEquals(500, r.getCode());
        assertFalse(r.getMessage().contains("db down"), r.getMessage());
    }
}