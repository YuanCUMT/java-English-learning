package com.dailyreading.service;

import com.dailyreading.entity.Article;
import com.dailyreading.mapper.ArticleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final Pattern EN_WORD = Pattern.compile("[A-Za-z][A-Za-z'\\-]*");

    private final PromptBuilder promptBuilder;
    private final DeepSeekClient deepSeek;
    private final ArticleMapper articleMapper;
    private final ObjectMapper mapper;

    public record Result(Article article, Map<String, Object> body) {}

    public boolean aiConfigured() {
        return deepSeek.configured();
    }

    /** 生成（或重新生成）当天某个模式的文章。 */
    public Result generate(Long userId, List<String> words, PromptBuilder.Options opt) {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("今天还没有单词，先导入再生成。");
        }
        JsonNode json = deepSeek.completeJson(promptBuilder.system(), promptBuilder.user(words, opt), 0.75);
        validate(json);

        int wordCount = countWords(json.path("paragraphs"));
        String content;
        try {
            content = mapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("文章序列化失败", e);
        }

        LocalDate today = LocalDate.now();
        // 同一用户同一天同一模式只能有一条：先删后插，避免撞唯一键
        articleMapper.deleteByUserDateMode(userId, today, opt.mode());

        Article a = new Article();
        a.setUserId(userId);
        a.setArticleDate(today);
        a.setMode(opt.mode());
        a.setTitle(trim(json.path("title").asText("Untitled"), 240));
        a.setSubtitle(trim(json.path("subtitle").asText(""), 240));
        a.setGist(trim(json.path("gist").asText(""), 480));
        a.setDifficulty(opt.difficulty());
        a.setWordCount(wordCount);
        a.setContent(content);
        articleMapper.insert(a);

        // 记下文章实际用到的词，后面「本篇用到 X/Y」靠它
        for (String w : usedWords(json)) {
            articleMapper.insertArticleWord(a.getId(), w, 1);
        }
        return new Result(a, toBody(content));
    }

    /** 当天最新一篇文章（任意模式），没有就返回 null。 */
    public Result findToday(Long userId) {
        Article a = articleMapper.findLatestOfDay(userId, LocalDate.now());
        if (a == null) return null;
        return new Result(a, toBody(a.getContent()));
    }

    public Map<String, Object> toBody(String content) {
        try {
            return mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("文章内容解析失败", e);
        }
    }

    // ---------------- 内部 ----------------

    private void validate(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new IllegalStateException("模型返回的不是一个 JSON 对象");
        }
        JsonNode ps = json.path("paragraphs");
        if (!ps.isArray() || ps.isEmpty()) {
            throw new IllegalStateException("模型没有给出正文段落，再试一次");
        }
        for (JsonNode p : ps) {
            if (p.path("en").asText("").isBlank()) {
                throw new IllegalStateException("模型返回的段落是空的，再试一次");
            }
        }
        JsonNode qs = json.path("questions");
        if (qs.isArray()) {
            for (JsonNode q : qs) {
                if (q.path("options").size() != 4) {
                    throw new IllegalStateException("有一道题的选项不是 4 个，再试一次");
                }
            }
        }
    }

    private int countWords(JsonNode paragraphs) {
        int n = 0;
        for (JsonNode p : paragraphs) {
            Matcher m = EN_WORD.matcher(p.path("en").asText(""));
            while (m.find()) n++;
        }
        return n;
    }

    private List<String> usedWords(JsonNode json) {
        Set<String> out = new LinkedHashSet<>();
        JsonNode ws = json.path("words");
        if (ws.isArray()) {
            for (JsonNode w : ws) {
                String v = w.path("word").asText("").trim();
                if (!v.isEmpty()) out.add(v.length() > 64 ? v.substring(0, 64) : v);
            }
        }
        return new ArrayList<>(out);
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
