package com.dailyreading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 模型返回的 JSON 靠提示词管不住，这里补结构化校验 —— 纯函数，好测。 */
class ArticleValidatorTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void 合法文章能通过() throws Exception {
        String good = """
                {"title":"T","paragraphs":[{"en":"This is a long enough paragraph for the test."}],
                 "questions":[{"q":"Q?","options":["A. a","B. b","C. c","D. d"],"answer":"B"}]}
                """;
        assertTrue(ArticleValidator.check(json(good), 1, 1).isEmpty());
    }

    @Test
    void 答案不是甲乙丙丁要报错() throws Exception {
        String bad = """
                {"title":"T","paragraphs":[{"en":"This is a long enough paragraph for the test."}],
                 "questions":[{"q":"Q?","options":["A. a","B. b","C. c","D. d"],"answer":"E"}]}
                """;
        List<String> problems = ArticleValidator.check(json(bad), 1, 1);
        assertTrue(problems.stream().anyMatch(p -> p.contains("A/B/C/D")), problems.toString());
    }

    @Test
    void 选项不足四个要报错() throws Exception {
        String bad = """
                {"title":"T","paragraphs":[{"en":"This is a long enough paragraph for the test."}],
                 "questions":[{"q":"Q?","options":["A. a","B. b"],"answer":"A"}]}
                """;
        assertTrue(ArticleValidator.check(json(bad), 1, 1).stream().anyMatch(p -> p.contains("4 个")));
    }

    @Test
    void 段落数和题数不符要报错() throws Exception {
        String shortOne = """
                {"title":"T","paragraphs":[{"en":"This is a long enough paragraph for the test."}],
                 "questions":[{"q":"Q?","options":["A. a","B. b","C. c","D. d"],"answer":"A"}]}
                """;
        assertFalse(ArticleValidator.check(json(shortOne), 3, 1).isEmpty());
        assertFalse(ArticleValidator.check(json(shortOne), 1, 5).isEmpty());
    }

    @Test
    void 空对象要报错() throws Exception {
        assertFalse(ArticleValidator.check(json("{}"), 1, 1).isEmpty());
    }

    @Test
    void 选项缺少ABCD前缀要报错() throws Exception {
        String noPrefix = """
                {"title":"t","paragraphs":[{"en":"This paragraph is long enough to pass the length check."}],
                 "questions":[{"q":"q1","options":["first option","second option","third option","fourth option"],"answer":"A"}]}
                """;
        assertFalse(ArticleValidator.check(json(noPrefix), 1, 1).isEmpty(), "没有 A./B./C./D. 前缀应该被拦下来");
    }

    @Test
    void 带前缀的选项不报错() throws Exception {
        String ok = """
                {"title":"t","paragraphs":[{"en":"This paragraph is long enough to pass the length check."}],
                 "questions":[{"q":"q1","options":["A. one","B. two","C. three","D. four"],"answer":"B"}]}
                """;
        assertTrue(ArticleValidator.check(json(ok), 1, 1).isEmpty());
    }

    @Test
    void 目标词按词形前缀匹配() throws Exception {
        String body = """
                {"paragraphs":[{"en":"She takes notes and taking breaks; the result was taken badly."}]}
                """;
        assertEquals(List.of("take"), ArticleValidator.matchWords(json(body), List.of("take")));
    }

    @Test
    void 没出现在正文里的词不算用过() throws Exception {
        String body = """
                {"paragraphs":[{"en":"Tool libraries are popular this year."}]}
                """;
        assertTrue(ArticleValidator.matchWords(json(body), List.of("abandon")).isEmpty());
    }

    @Test
    void 短语按整串匹配() throws Exception {
        String body = """
                {"paragraphs":[{"en":"In terms of cost, it is cheaper."}]}
                """;
        assertEquals(List.of("in terms of"), ArticleValidator.matchWords(json(body), List.of("in terms of")));
        assertTrue(ArticleValidator.matchWords(json(body), List.of("in term of")).isEmpty());
    }
}
