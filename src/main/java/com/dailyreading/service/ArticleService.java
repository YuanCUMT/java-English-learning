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
    private final SettingService settingService;
    private final ArticleStore articleStore;
    private final ArticleMapper articleMapper;
    private final DictService dictService;
    private final com.dailyreading.mapper.LookupCacheMapper lookupCacheMapper;
    private final ObjectMapper mapper;

    public record Result(Article article, Map<String, Object> body) {}

    /** 过滤的结果：能用的词、被判为认错碎片的词。 */
    record Targets(List<String> kept, List<String> dropped) {}

    public boolean aiConfigured(Long userId) {
        return settingService.deepSeekConfigured(userId);
    }

    /** 生成（或重新生成）当天某个模式的文章。 */
    public Result generate(Long userId, List<String> words, PromptBuilder.Options opt) {
        if (words == null || words.isEmpty()) {
            throw new IllegalArgumentException("今天还没有单词，先导入再生成。");
        }
        // 截图识别会带出点碎片（bl、da 这种），先剔掉再喂给模型
        Targets targets = pickTargets(userId, words);
        if (targets.kept().isEmpty()) {
            throw new IllegalArgumentException("今天的词看着都是截图认错的（"
                    + String.join("、", targets.dropped()) + "），重新导一次截图吧。");
        }
        JsonNode json = deepSeek.completeJson(promptBuilder.system(), promptBuilder.user(targets.kept(), opt), 0.75, settingService.effectiveDeepSeekKey(userId));
        validate(json, opt);

        int wordCount = countWords(json.path("paragraphs"));
        String content;
        try {
            content = mapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new IllegalStateException("文章序列化失败", e);
        }

        LocalDate today = LocalDate.now();

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

        // 先删同一天的旧文再插入 + 写 article_words，整段在一个事务里
        List<String> actualUsed = ArticleValidator.matchWords(json, targets.kept());
        if (actualUsed.isEmpty()) {
            throw new IllegalStateException("生成的文章里一个目标词都没用上，再生成一次试试。");
        }
        // article_words 记「正文里真的出现了哪些目标词」，不是模型自己声明的清单
        articleStore.replace(a, actualUsed);
        Map<String, Object> body = toBody(content);
        if (!targets.dropped().isEmpty()) {
            body.put("skippedWords", targets.dropped());
        }
        return new Result(a, body);
    }

    /**
     * 挑出真正能进文章的词。
     * 判定「像词」：词典或查词缓存里查得到，或者长度 ≥3 且含元音；
     * 否则当作截图识别的碎片丢掉（bl、da、bsh 这种）。
     */
    Targets pickTargets(Long userId, List<String> words) {
        List<String> kept = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        for (String raw : words) {
            String w = raw == null ? "" : raw.trim();
            if (w.isEmpty()) continue;
            String t = w.toLowerCase();
            boolean known = dictService.lookup(t) != null
                    || lookupCacheMapper.findPayload(userId, t) != null;
            boolean plausible = t.length() >= 3
                    && t.chars().anyMatch(c -> "aeiouy".indexOf(c) >= 0);
            if (known || plausible) kept.add(w);
            else dropped.add(w);
        }
        return new Targets(kept, dropped);
    }

    /** 当天最新一篇文章（任意模式），没有就返回 null。 */
    public Result findToday(Long userId) {
        Article a = articleMapper.findLatestOfDay(userId, LocalDate.now());
        if (a == null) return null;
        return new Result(a, toBody(a.getContent()));
    }

    /** 按 id 取一篇（只能取自己的），用于历史文章阅读。 */
    public Result findById(Long userId, Long id) {
        Article a = articleMapper.findById(id, userId);
        if (a == null) return null;
        return new Result(a, toBody(a.getContent()));
    }

    public List<Article> list(Long userId) {
        return articleMapper.listByUser(userId);
    }

    public Map<String, Object> toBody(String content) {
        try {
            return mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("文章内容解析失败", e);
        }
    }

    // ---------------- 内部 ----------------

    private void validate(JsonNode json, PromptBuilder.Options opt) {
        List<String> problems = ArticleValidator.check(json, opt.paragraphs(), opt.questions());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("模型返回的内容不合规（" + String.join("；", problems) + "），再试一次");
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
