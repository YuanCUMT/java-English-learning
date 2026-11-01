package com.dailyreading.service;

import com.dailyreading.entity.Article;
import com.dailyreading.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 只负责「落库」这一小段，用事务包住。
 * AI 调用放在外面，不塞进长事务里。
 */
@Service
@RequiredArgsConstructor
public class ArticleStore {

    private final ArticleMapper articleMapper;

    @Transactional
    public Article replace(Article a, List<String> usedWords) {
        articleMapper.deleteByUserDateMode(a.getUserId(), a.getArticleDate(), a.getMode());
        articleMapper.insert(a);
        for (String w : usedWords) {
            articleMapper.insertArticleWord(a.getId(), w, 1);
        }
        return a;
    }
}
