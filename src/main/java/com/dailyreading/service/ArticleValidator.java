package com.dailyreading.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
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
                String answer = q.path("answer").asText("").trim().toUpperCase();
                if (!OPTION_KEYS.contains(answer)) {
                    problems.add("第 " + (i + 1) + " 题答案不是 A/B/C/D：" + answer);
                }
            }
        }
        return problems;
    }
}
