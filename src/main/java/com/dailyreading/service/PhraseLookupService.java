package com.dailyreading.service;

import com.dailyreading.mapper.LookupCacheMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地词典查不到的东西（短语、专有名词、生僻组合）走这里。
 * 先用 lookup_cache 挡一层，同一个词只花一次钱。
 */
@Service
@RequiredArgsConstructor
public class PhraseLookupService {

    private final DeepSeekClient deepSeek;
    private final LookupCacheMapper cacheMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public Map<String, Object> lookup(Long userId, String raw) throws Exception {
        String word = normalize(raw);
        if (word.isEmpty()) return null;

        String cached = cacheMapper.findPayload(userId, word);
        if (cached != null) {
            Map<String, Object> hit = parse(cached, word);
            hit.put("source", "cache");
            return hit;
        }

        String system = """
                你是考研英语阅读老师。用户在读英文文章时点了一个词或短语，请给出它在这里最可能需要的中文意思。
                只输出 JSON，不要任何解释，格式：
                {"word":"原形或原短语","phonetic":"音标，没有就空字符串","pos":"词性，如 n./v./adj./phr.","cn":"中文释义，短语可以给两三种常见译法，用分号隔开","collocation":"常见搭配，没有就空字符串","example":"一个简短的英文例句"}
                短语（两个词以上）的 pos 一律填 phr.；不要编造音标。
                """;
        String user = "要查的词或短语：" + word;

        JsonNode node = deepSeek.completeJson(system, user, 0.2);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("word", text(node, "word", word));
        data.put("phonetic", text(node, "phonetic", ""));
        data.put("pos", text(node, "pos", ""));
        data.put("cn", text(node, "cn", ""));
        data.put("collocation", text(node, "collocation", ""));
        data.put("example", text(node, "example", ""));
        List<String> tags = new ArrayList<>();
        tags.add("AI");
        data.put("tags", tags);
        data.put("source", "model");

        cacheMapper.save(userId, word, "model", objectMapper.writeValueAsString(data));
        return data;
    }

    private Map<String, Object> parse(String json, String fallbackWord) {
        try {
            JsonNode node = objectMapper.readTree(json);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("word", text(node, "word", fallbackWord));
            data.put("phonetic", text(node, "phonetic", ""));
            data.put("pos", text(node, "pos", ""));
            data.put("cn", text(node, "cn", ""));
            data.put("collocation", text(node, "collocation", ""));
            data.put("example", text(node, "example", ""));
            List<String> tags = new ArrayList<>();
            tags.add("AI");
            data.put("tags", tags);
            return data;
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("word", fallbackWord);
            data.put("cn", json);
            return data;
        }
    }

    private String text(JsonNode node, String field, String def) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? def : v.asText(def);
    }
}
