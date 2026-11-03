package com.dailyreading.config;

import com.dailyreading.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 把外观偏好注入所有页面模型，模板里用 th:attr="data-theme=${theme}" 渲染到 <html> 上。
 * 没登录的页面（登录/注册）拿不到 userId，就走 auto（跟随系统）。
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ThemeAdvice {

    private final SettingService settingService;

    @ModelAttribute("theme")
    public String theme(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        Long userId = uid instanceof Long l ? l : null;
        return settingService.theme(userId);
    }
}
