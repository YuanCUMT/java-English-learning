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
 * 统一异常出口。ApiStatusAdvice 同步业务码与 HTTP 错误状态。
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse badRequest(IllegalArgumentException e) {
        return ApiResponse.fail(400, e.getMessage() == null ? "参数不对" : e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse illegalState(IllegalStateException e) {
        log.warn("业务操作失败：{}",e.getClass().getSimpleName());
        return ApiResponse.fail(500, e.getMessage() == null ? "操作失败" : e.getMessage());
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            java.time.format.DateTimeParseException.class})
    public ApiResponse invalidInput(Exception e) { return ApiResponse.fail(400,"参数或 JSON 格式不正确，请检查日期与输入内容"); }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ApiResponse tooLarge(Exception e) { return ApiResponse.fail(413,"上传文件过大，单张图片不得超过 10 MB"); }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ApiResponse status(org.springframework.web.server.ResponseStatusException e) { return ApiResponse.fail(e.getStatusCode().value(),e.getReason()); }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ApiResponse method(Exception e) { return ApiResponse.fail(405,"请求方法不支持"); }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse unexpected(Exception e) {
        log.error("未预期的异常", e);
        return ApiResponse.fail(500, "服务器暂时无法完成操作，请稍后重试");
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
