package com.dailyreading.mapper;

import com.dailyreading.entity.Article;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ArticleMapper {

    @Select("""
            SELECT id, user_id, article_date, mode, title, subtitle, gist, difficulty, word_count, content, created_at
              FROM articles
             WHERE user_id = #{userId} AND article_date = #{date} AND mode = #{mode}
            """)
    Article findByUserDateMode(@Param("userId") Long userId,
                               @Param("date") LocalDate date,
                               @Param("mode") String mode);

    @Select("""
            SELECT id, user_id, article_date, mode, title, subtitle, gist, difficulty, word_count, content, created_at
              FROM articles
             WHERE user_id = #{userId} AND article_date = #{date}
             ORDER BY id DESC LIMIT 1
            """)
    Article findLatestOfDay(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("""
            SELECT id, user_id, article_date, mode, title, subtitle, gist, difficulty, word_count,
                   NULL AS content, created_at
              FROM articles
             WHERE user_id = #{userId}
             ORDER BY article_date DESC, id DESC
            """)
    List<Article> listByUser(@Param("userId") Long userId);

    @Select("""
            SELECT id, user_id, article_date, mode, title, subtitle, gist, difficulty, word_count, content, created_at
              FROM articles
             WHERE id = #{id} AND user_id = #{userId}
            """)
    Article findById(@Param("id") Long id, @Param("userId") Long userId);

    @Insert("""
            INSERT INTO articles (user_id, article_date, mode, title, subtitle, gist, difficulty, word_count, content)
            VALUES (#{userId}, #{articleDate}, #{mode}, #{title}, #{subtitle}, #{gist}, #{difficulty}, #{wordCount}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Article article);

    @Delete("""
            DELETE FROM articles
             WHERE user_id = #{userId} AND article_date = #{date} AND mode = #{mode}
            """)
    int deleteByUserDateMode(@Param("userId") Long userId,
                             @Param("date") LocalDate date,
                             @Param("mode") String mode);

    @Delete("DELETE FROM articles WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("userId") Long userId, @Param("id") Long id);

    @Insert("INSERT INTO article_words (article_id, word, is_target) VALUES (#{articleId}, #{word}, #{isTarget})")
    int insertArticleWord(@Param("articleId") Long articleId,
                          @Param("word") String word,
                          @Param("isTarget") int isTarget);
}
