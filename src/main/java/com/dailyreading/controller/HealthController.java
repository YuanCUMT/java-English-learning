package com.dailyreading.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：只用来确认脚手架跑通了，后面会被真正的接口取代。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.boot.availability.ApplicationAvailability availability;
    @org.springframework.beans.factory.annotation.Value("${app.demo:false}")
    private boolean demo;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("message", "ok");
        body.put("data", Map.of("app", "daily-reading", "status", "up", "mode", demo ? "demo" : "mysql",
                "ready", availability.getReadinessState() == org.springframework.boot.availability.ReadinessState.ACCEPTING_TRAFFIC));
        return body;
    }
}
