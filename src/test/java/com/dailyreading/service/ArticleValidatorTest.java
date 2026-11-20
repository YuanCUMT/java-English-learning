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
        var node=om.readTree(s);
        if(node.path("title").asText("").equals("T") || node.path("title").asText("").equals("t")) {
            String en=node.path("paragraphs").get(0).path("en").asText();
            for(var para:node.path("paragraphs")) ((com.fasterxml.jackson.databind.node.ObjectNode)para).put("cn","译文");
            for(var q:node.path("questions")) { ((com.fasterxml.jackson.databind.node.ObjectNode)q).put("explain","解释").put("locate",en); }
            ((com.fasterxml.jackson.databind.node.ObjectNode)node).set("words",om.readTree("[{\"word\":\"test\",\"cn\":\"测试\",\"pos\":\"n.\",\"example\":\"A test.\"}]"));
            var sentences=om.createArrayNode();
            for(int i=0;i<2;i++) sentences.addObject().put("sent",en).put("structure","结构").put("meaning","含义");
            ((com.fasterxml.jackson.databind.node.ObjectNode)node).set("sentences",sentences);
        }
        return node;
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
    void 纯函数只按整词匹配() throws Exception {
        String body = """
                {"paragraphs":[{"en":"She takes notes and taking breaks; the result was taken badly."}]}
                """;
        assertTrue(ArticleValidator.matchWords(json(body), List.of("take")).isEmpty());
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
