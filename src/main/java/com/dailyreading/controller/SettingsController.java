package com.dailyreading.controller;

import com.dailyreading.common.ApiResponse;
import com.dailyreading.service.DeepSeekClient;
import com.dailyreading.service.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设置页：让用户自己填 DeepSeek API Key，不用改服务器上的配置文件。
 * Key 只存在本机 MySQL 的 user_settings 表里，不往外发。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;
    private final DeepSeekClient deepSeek;

    public record SaveRequest(String deepseekKey) {}

    @GetMapping
    public ApiResponse get(@RequestAttribute("userId") Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deepseekKeyMasked", settingService.maskedDeepSeekKey(userId));
        data.put("deepseekConfigured", settingService.deepSeekConfigured(userId));
        data.put("usingOwnKey", settingService.userDeepSeekKey(userId) != null);
        return ApiResponse.ok(data);
    }

    @PostMapping
    public ApiResponse save(@RequestAttribute("userId") Long userId, @RequestBody SaveRequest req) {
        String key = req.deepseekKey() == null ? "" : req.deepseekKey().trim();
        if (!key.isEmpty() && !key.startsWith("sk-")) {
            return ApiResponse.fail(400, "DeepSeek 的 Key 一般以 sk- 开头，检查一下是不是复制少了");
        }
        settingService.saveDeepSeekKey(userId, key);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deepseekKeyMasked", settingService.maskedDeepSeekKey(userId));
        data.put("deepseekConfigured", settingService.deepSeekConfigured(userId));
        data.put("usingOwnKey", settingService.userDeepSeekKey(userId) != null);
        return ApiResponse.ok(data);
    }

    @DeleteMapping
    public ApiResponse clear(@RequestAttribute("userId") Long userId) {
        settingService.saveDeepSeekKey(userId, "");
        return ApiResponse.ok(Map.of("ok", true));
    }

    /** 拿这个 Key 真发一次最小的请求，确认能用 */
    @PostMapping("/test")
    public ApiResponse test(@RequestAttribute("userId") Long userId) {
        String key = settingService.effectiveDeepSeekKey(userId);
        if (key == null || key.isBlank()) {
            return ApiResponse.fail(400, "还没填 Key");
        }
        long start = System.currentTimeMillis();
        try {
            deepSeek.completeJson("你是一个测试用助手，只回 JSON。", "{\"ping\":1}", 0, key);
            return ApiResponse.ok(Map.of("ok", true, "ms", System.currentTimeMillis() - start));
        } catch (Exception e) {
            return ApiResponse.fail(500, "这个 Key 用不了：" + e.getMessage());
        }
    }
}
