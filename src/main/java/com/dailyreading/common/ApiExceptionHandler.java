package com.dailyreading.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 统一异常出口：业务异常一律 HTTP 200 + {code,message}，
 * 只有没登录（拦截器返回 401）才走 HTTP 状态码。
 * 这样前端只需要看 code，不用每个接口各写一套判断。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse badRequest(IllegalArgumentException e) {
        return ApiResponse.fail(400, e.getMessage() == null ? "参数不对" : e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse illegalState(IllegalStateException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.fail(500, e.getMessage() == null ? "操作失败" : e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse unexpected(Exception e) {
        log.error("未预期的异常", e);
        return ApiResponse.fail(500, "服务器出了点问题：" + e.getClass().getSimpleName());
    }

    /**
     * 静态资源没找到（favicon.ico、浏览器扩展探测的 /hybridaction/... 等）。
     * 这种不是程序出错：打 404、只记 debug，别在启动日志里刷一片 ERROR 堆栈吓人。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse notFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());
        return ApiResponse.fail(404, "没有这个地址");
    }

    /** 请求体格式没对上（最常见：前端忘了带 Content-Type: application/json） */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ApiResponse mediaType(HttpMediaTypeNotSupportedException e) {
        return ApiResponse.fail(415, "请求格式不对：" + e.getContentType()
                + "，这个接口只收 application/json");
    }
}
