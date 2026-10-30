package com.dailyreading.service.ocr;

import com.dailyreading.config.OcrProperties;
import com.dailyreading.service.WordService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 百度云通用文字识别。provider=baidu 时才装配。
 * 文档：https://ai.baidu.com/ai-doc/OCR/1k3h7y3db
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "baidu")
public class BaiduOcrService implements OcrService {

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";

    private final OcrProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client = RestClient.create();

    private volatile String token;
    private volatile long tokenExpireAt;

    @Override
    public List<String> recognize(byte[] image) {
        OcrProperties.Baidu cfg = props.getBaidu();
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()
                || cfg.getSecretKey() == null || cfg.getSecretKey().isBlank()) {
            throw new IllegalStateException("还没配置百度 OCR 的 API Key / Secret Key");
        }

        String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/"
                + (cfg.isAccurate() ? "accurate_basic" : "general_basic")
                + "?access_token=" + accessToken();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("image", Base64.getEncoder().encodeToString(image));
        form.add("detect_language", "true");

        String body = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

        return parse(body);
    }

    private List<String> parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.has("error_code")) {
                throw new IllegalStateException("百度 OCR 返回错误 "
                        + root.path("error_code").asInt() + "：" + root.path("error_msg").asText());
            }
            List<String> lines = new ArrayList<>();
            for (JsonNode item : root.path("words_result")) {
                lines.add(item.path("words").asText(""));
            }
            // 逐行抽词，保留原有顺序
            return WordService.extract(String.join("\n", lines));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析百度 OCR 结果失败：" + e.getMessage(), e);
        }
    }

    /** access_token 有效期 30 天，缓存起来别每次都换 */
    private synchronized String accessToken() {
        if (token != null && System.currentTimeMillis() < tokenExpireAt) {
            return token;
        }
        OcrProperties.Baidu cfg = props.getBaidu();
        String body = client.post()
                .uri(TOKEN_URL + "?grant_type=client_credentials&client_id={id}&client_secret={secret}",
                        cfg.getApiKey(), cfg.getSecretKey())
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = mapper.readTree(body);
            if (root.path("access_token").isMissingNode()) {
                throw new IllegalStateException("换取百度 access_token 失败：" + body);
            }
            token = root.path("access_token").asText();
            long expires = root.path("expires_in").asLong(2592000);
            tokenExpireAt = System.currentTimeMillis() + (expires - 300) * 1000L;
            return token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("换取百度 access_token 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String engine() {
        return "baidu " + (props.getBaidu().isAccurate() ? "高精度版" : "标准版");
    }
}
