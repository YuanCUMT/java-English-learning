package com.dailyreading.service;

import com.dailyreading.config.DeepSeekProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek（OpenAI 兼容）调用。只负责「发请求 + 把 JSON 拿回来」，
 * 提示词和校验放在 PromptBuilder / ArticleService 里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekClient {

    private final DeepSeekProperties props;
    private final ObjectMapper mapper;

    private RestClient client() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .build();
    }

    public boolean configured() {
        return props.configured();
    }

    /**
     * 让模型返回一个 JSON 对象。失败时抛出 IllegalStateException（带人话信息）。
     */
    public JsonNode completeJson(String systemPrompt, String userPrompt, double temperature) {
        if (!configured()) {
            throw new IllegalStateException("还没配置 DeepSeek API Key，文章生成需要它。");
        }
        Map<String, Object> payload = Map.of(
                "model", props.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", temperature,
                "max_tokens", 4096,
                "response_format", Map.of("type", "json_object")
        );

        String raw;
        try {
            raw = client().post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("调用 DeepSeek 失败: {}", e.getMessage());
            throw new IllegalStateException("调用 DeepSeek 失败：" + e.getMessage(), e);
        }

        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("模型没有返回内容");
            }
            return mapper.readTree(stripFence(content.asText()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + e.getMessage(), e);
        }
    }

    /** 有些时候模型仍会包一层 ```json，兜一下 */
    private String stripFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
