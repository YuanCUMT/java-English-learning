package com.dailyreading.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/** 查词缓存：同一句话/同一个短语只花一次钱。 */
@Mapper
public interface LookupCacheMapper {

    @Select("SELECT payload FROM lookup_cache WHERE user_id = #{userId} AND word = #{word} LIMIT 1")
    String findPayload(@Param("userId") Long userId, @Param("word") String word);

    @Insert("""
            INSERT INTO lookup_cache (user_id, word, source, payload)
            VALUES (#{userId}, #{word}, #{source}, #{payload})
            ON DUPLICATE KEY UPDATE payload = VALUES(payload)
            """)
    int save(@Param("userId") Long userId, @Param("word") String word,
             @Param("source") String source, @Param("payload") String payload);
}
