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
}
