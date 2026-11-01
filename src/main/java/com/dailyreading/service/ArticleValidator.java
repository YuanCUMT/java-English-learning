package com.dailyreading.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型返回的 JSON 靠提示词约束不可靠，这里做结构化校验。
 * 返回问题列表，空列表表示通过。
 */
public final class ArticleValidator {

    private static final Set<String> OPTION_KEYS = Set.of("A", "B", "C", "D");

    private ArticleValidator() {}

    public static List<String> check(JsonNode json, int wantParagraphs, int wantQuestions) {
        List<String> problems = new ArrayList<>();
        if (json == null || !json.isObject()) {
            problems.add("返回的不是 JSON 对象");
            return problems;
        }
        if (json.path("title").asText("").isBlank()) problems.add("标题为空");

        JsonNode paragraphs = json.path("paragraphs");
        if (!paragraphs.isArray() || paragraphs.isEmpty()) {
            problems.add("没有段落");
        } else {
            if (wantParagraphs > 0 && paragraphs.size() != wantParagraphs) {
                problems.add("段落数 " + paragraphs.size() + "，要求 " + wantParagraphs);
            }
            for (int i = 0; i < paragraphs.size(); i++) {
                String en = paragraphs.get(i).path("en").asText("");
                if (en.strip().length() < 20) problems.add("第 " + (i + 1) + " 段太短");
            }
        }

        JsonNode questions = json.path("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            problems.add("没有题目");
        } else {
            if (wantQuestions > 0 && questions.size() != wantQuestions) {
                problems.add("题目数 " + questions.size() + "，要求 " + wantQuestions);
            }
            for (int i = 0; i < questions.size(); i++) {
                JsonNode q = questions.get(i);
                if (q.path("q").asText("").isBlank()) problems.add("第 " + (i + 1) + " 题题干为空");
                JsonNode opts = q.path("options");
                if (!opts.isArray() || opts.size() != 4) {
                    problems.add("第 " + (i + 1) + " 题选项不是 4 个");
                }
                if (opts.isArray()) {
                    for (int j = 0; j < opts.size(); j++) {
                        String text = opts.get(j).asText("").trim();
                        if (!text.toUpperCase().startsWith(String.valueOf((char) ('A' + j)))) {
                            problems.add("第 " + (i + 1) + " 题第 " + (j + 1) + " 个选项没有 A./B./C./D. 前缀：" + text);
                        }
                    }
                }
                String answer = q.path("answer").asText("").trim().toUpperCase();
                if (!OPTION_KEYS.contains(answer)) {
                    problems.add("第 " + (i + 1) + " 题答案不是 A/B/C/D：" + answer);
                }
            }
        }
        return problems;
    }

    /**
     * 从正文里实际找目标词，而不是信模型自己在 words 数组里报的那份。
     * 单词按词形前缀匹配（take / takes / taking 都算），短语按整串匹配。
     * 这样 article_words 记的就是"正文里真出现了什么"，老师问起来才有得答。
     */
    public static List<String> matchWords(JsonNode json, List<String> targets) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : json.path("paragraphs")) {
            sb.append(' ').append(p.path("en").asText(""));
        }
        String raw = (" " + sb + " ").toLowerCase();
        String flat = raw.replaceAll("[^a-z0-9'\\-]+", " ");
        String[] tokens = flat.trim().split("\\s+");
        Set<String> out = new LinkedHashSet<>();
        if (targets == null) return new ArrayList<>(out);
        for (String t : targets) {
            String key = t == null ? "" : t.trim().toLowerCase();
            if (key.isEmpty()) continue;
            boolean hit;
            if (key.contains(" ")) {
                hit = flat.contains(" " + key + " ");
            } else {
                hit = false;
                for (String tok : tokens) {
                    if (tok.equals(key) || (tok.startsWith(key) && tok.length() <= key.length() + 3)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) out.add(key);
        }
        return new ArrayList<>(out);
    }
}
