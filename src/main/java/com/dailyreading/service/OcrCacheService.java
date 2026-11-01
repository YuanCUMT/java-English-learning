package com.dailyreading.service;

import com.dailyreading.mapper.OcrCacheMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片识别结果缓存。
 *
 * <p>缓存键是图片字节的 sha1，所以「同一张图再拖一次」不会再去调百度/tesseract；
 * 内容不同但长得像的图仍然照常识别。命中不了就当没有，不影响主流程。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OcrCacheService {

    private final OcrCacheMapper mapper;
    private final ObjectMapper json;

    /** 缓存里存的东西：识别到的词 + 当时是哪个引擎识别的 */
    public record Cached(List<String> words, String engine) {}

    public static String sha1(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // SHA-1 必然存在；真出问题就退化成「不缓存」，不能挡住识别
            return "";
        }
    }

    public Cached get(String sha1) {
        if (sha1 == null || sha1.isEmpty()) return null;
        try {
            String payload = mapper.findPayload(sha1);
            if (payload == null || payload.isBlank()) return null;
            JsonNode node = json.readTree(payload);
            List<String> words = new ArrayList<>();
            for (JsonNode w : node.path("words")) {
                if (w.isTextual() && !w.asText().isBlank()) words.add(w.asText());
            }
            if (words.isEmpty()) return null;
            return new Cached(words, node.path("engine").asText("缓存"));
        } catch (Exception e) {
            log.warn("OCR 缓存读取失败，忽略：{}", e.getMessage());
            return null;
        }
    }

    public void put(String sha1, List<String> words, String engine) {
        if (sha1 == null || sha1.isEmpty() || words == null || words.isEmpty()) return;
        try {
            ObjectNode node = json.createObjectNode();
            ArrayNode arr = node.putArray("words");
            words.forEach(arr::add);
            node.put("engine", engine == null ? "" : engine);
            mapper.save(sha1, json.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("OCR 缓存写入失败，忽略：{}", e.getMessage());
        }
    }
}
