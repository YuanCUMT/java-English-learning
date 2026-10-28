package com.dailyreading.mapper;

import com.dailyreading.entity.DictEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
