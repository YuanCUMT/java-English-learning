package com.dailyreading.controller;

import com.dailyreading.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 页面级异常兜底：业务异常回显到当前页，其它异常转到 500。
 */
@ControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler(UserService.BizException.class)
    public String handleBiz(UserService.BizException e, Model model, HttpSession session) {
        model.addAttribute("error", e.getMessage());
        return "error";
    }
}
