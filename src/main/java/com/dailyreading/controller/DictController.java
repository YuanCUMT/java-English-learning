package com.dailyreading.controller;

import com.dailyreading.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M1 验证接口：证明 Spring -> MyBatis -> MySQL -> 词典 这条链路是通的。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam String word) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> hit = dictService.lookup(word);
        if (hit == null) {
            body.put("code", 404);
            body.put("message", "本地词典没有这个词");
            body.put("data", null);
            return body;
        }
        body.put("code", 0);
        body.put("message", "ok");
        body.put("data", hit);
        return body;
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
