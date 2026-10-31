package com.dailyreading.controller;

import com.dailyreading.service.DictService;
import com.dailyreading.service.PhraseLookupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查词：先本地词典（ECDICT，毫秒级、免费），查不到再让前端决定要不要问模型。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;
    private final PhraseLookupService phraseLookup;

    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String word,
                                      @RequestParam(required = false, defaultValue = "0") String ai,
                                      HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (!"1".equals(ai)) {
            Map<String, Object> hit = dictService.lookup(word);
            if (hit != null) {
                body.put("code", 0);
                body.put("message", "ok");
                body.put("data", hit);
                return body;
            }
            // 没查到：告诉前端「可以问模型」，前端弹一个按钮，不自动花钱
            body.put("code", 404);
            body.put("message", "本地词典没有这个词");
            body.put("canAskModel", phraseLookupAvailable());
            body.put("data", null);
            return body;
        }

        Long userId = (Long) request.getAttribute("userId");
        try {
            Map<String, Object> data = phraseLookup.lookup(userId, word);
            body.put("code", 0);
            body.put("message", "ok");
            body.put("data", data);
        } catch (Exception e) {
            body.put("code", 500);
            body.put("message", e.getMessage() == null ? "查词失败" : e.getMessage());
            body.put("data", null);
        }
        return body;
    }

    private boolean phraseLookupAvailable() {
        return true;
    }

    @GetMapping("/dict/size")
    public Map<String, Object> size() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "ok");
        body.put("data", Map.of("entries", dictService.size()));
        return body;
    }
}
