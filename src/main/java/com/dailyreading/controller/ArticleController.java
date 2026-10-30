package com.dailyreading.controller;

import com.dailyreading.common.ApiResponse;
import com.dailyreading.entity.User;
import com.dailyreading.service.ArticleService;
import com.dailyreading.service.PromptBuilder;
import com.dailyreading.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final WordService wordService;

    public record GenerateRequest(String mode, String difficulty, Integer paragraphs,
                                  Integer questions, String topic) {}

    @GetMapping("/status")
    public ApiResponse status() {
        return ApiResponse.ok(Map.of("aiConfigured", articleService.aiConfigured()));
    }

    @GetMapping("/today")
    public ApiResponse today(@RequestAttribute("userId") Long userId) {
        var r = articleService.findToday(userId);
        if (r == null) return ApiResponse.ok(null);
        return ApiResponse.ok(withMeta(r));
    }

    @GetMapping("/{id}")
    public ApiResponse byId(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        var r = articleService.findById(userId, id);
        if (r == null) return ApiResponse.fail(404, "文章不存在");
        return ApiResponse.ok(withMeta(r));
    }

    @PostMapping("/generate")
    public ApiResponse generate(@RequestAttribute("userId") Long userId,
                                                     @RequestBody(required = false) GenerateRequest req) {
        GenerateRequest r = req == null ? new GenerateRequest(null, null, null, null, null) : req;
        String mode = "exam".equalsIgnoreCase(r.mode()) ? "exam" : "review";

        List<String> words = wordService.listByDate(userId, LocalDate.now()).stream()
                .map(w -> w.getWord())
                .toList();
        if (words.isEmpty()) {
            return ApiResponse.fail(4001, "今天还没有单词，先导入再生成。");
        }

        PromptBuilder.Options opt = new PromptBuilder.Options(
                mode,
                blankTo(r.difficulty(), "C1"),
                clamp(r.paragraphs(), 3, 8, 5),
                clamp(r.questions(), 3, 8, 5),
                15,
                r.topic());

        try {
            return ApiResponse.ok(withMeta(articleService.generate(userId, words, opt)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.fail(4002, e.getMessage());
        }
    }

    private Map<String, Object> withMeta(ArticleService.Result r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", r.article().getId());
        m.put("mode", r.article().getMode());
        m.put("date", r.article().getArticleDate().toString());
        m.put("difficulty", r.article().getDifficulty());
        m.put("wordCount", r.article().getWordCount());
        m.put("body", r.body());
        return m;
    }

    private String blankTo(String s, String dflt) {
        return (s == null || s.isBlank()) ? dflt : s;
    }

    private int clamp(Integer v, int min, int max, int dflt) {
        if (v == null) return dflt;
        return Math.max(min, Math.min(max, v));
    }
}
