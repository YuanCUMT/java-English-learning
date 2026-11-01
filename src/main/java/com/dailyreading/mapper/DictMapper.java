package com.dailyreading.mapper;

import com.dailyreading.entity.DictEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DictMapper {

    @Select("SELECT word, phonetic, pos, translation, tag, collins, oxford "
            + "FROM dict_entries WHERE word = #{word}")
    DictEntry findByWord(@Param("word") String word);

    /** takes -> take；查不到返回 null */
    @Select("SELECT lemma FROM dict_forms WHERE form = #{form}")
    String findLemma(@Param("form") String form);

    @Select("SELECT COUNT(*) FROM dict_entries")
    int countEntries();
    /** 批量查词条，避免单词本历史逐词查导致的 N+1 */
    @Select("<script>SELECT word, phonetic, pos, translation, tag, collins, oxford FROM dict_entries "
            + "WHERE word IN <foreach item='w' collection='words' open='(' separator=',' close=')'>#{w}</foreach></script>")
    List<DictEntry> findByWords(@Param("words") List<String> words);

    /** 批量查词形 → 原形 */
    @Select("<script>SELECT form, lemma FROM dict_forms "
            + "WHERE form IN <foreach item='f' collection='forms' open='(' separator=',' close=')'>#{f}</foreach></script>")
    List<Map<String, Object>> findLemmas(@Param("forms") List<String> forms);
}