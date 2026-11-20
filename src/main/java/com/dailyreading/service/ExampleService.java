package com.dailyreading.service;
import com.dailyreading.entity.Article;
import com.dailyreading.mapper.ArticleMapper;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExampleService {
    private final ArticleMapper articles;
    private final ArticleStore store;
    private final WordService words;
    private final ObjectMapper mapper;
    private final TargetWordMatcher matcher;
    @Transactional
    public Long load(Long userId) {
        Article existing=articles.findByUserDateMode(userId,LocalDate.now(),"review");
        if(existing!=null) return existing.getId(); // never overwrite existing study material
        try(var input=new ClassPathResource("demo/article.json").getInputStream()) {
            JsonNode json=mapper.readTree(input);
            List<String> problems=ArticleValidator.check(json,5,5);
            if(!problems.isEmpty()) throw new IllegalStateException("随包示例结构错误："+problems);
            List<String> targets=new ArrayList<>(); json.path("words").forEach(w -> targets.add(w.path("word").asText()));
            var coverage=matcher.measure(json,targets,"review",15);
            ((com.fasterxml.jackson.databind.node.ObjectNode)json).set("coverage",mapper.valueToTree(coverage));
            words.importWords(userId,LocalDate.now(),targets);
            Article a=new Article(); a.setUserId(userId); a.setArticleDate(LocalDate.now()); a.setMode("review");
            a.setTitle(json.path("title").asText()); a.setSubtitle("固定示例 · 非实时生成"); a.setGist(json.path("gist").asText());
            StringBuilder english=new StringBuilder(); json.path("paragraphs").forEach(p->english.append(' ').append(p.path("en").asText()));
            a.setDifficulty("B2"); a.setWordCount((int)java.util.regex.Pattern.compile("[A-Za-z]+(?:['-][A-Za-z]+)*").matcher(english).results().count());
            a.setContent(mapper.writeValueAsString(json)); store.replace(a,coverage.matched()); return a.getId();
        } catch(java.io.IOException e) { throw new IllegalStateException("无法读取随包示例",e); }
    }
}
