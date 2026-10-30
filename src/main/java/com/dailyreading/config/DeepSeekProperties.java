package com.dailyreading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.deepseek")
public class DeepSeekProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";

    public boolean configured() {
        return apiKey != null && apiKey.startsWith("sk-") && apiKey.length() > 10;
    }
}
