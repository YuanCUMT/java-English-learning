package com.dailyreading.mapper;

import com.dailyreading.entity.Word;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WordMapper {

    /** 同一天同一个词只存一条；返回受影响行数，0 表示重复 */
    @Insert("INSERT IGNORE INTO words (user_id, word, surface, batch_date) "
            + "VALUES (#{userId}, #{word}, #{surface}, #{batchDate})")
    int insertIgnore(@Param("userId") Long userId,
                     @Param("word") String word,
                     @Param("surface") String surface,
                     @Param("batchDate") LocalDate batchDate);

    @Select("SELECT id, user_id, word, surface, batch_date, created_at FROM words "
            + "WHERE user_id = #{userId} AND batch_date = #{date} ORDER BY word")
    @Results(id = "wordMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "word", property = "word"),
            @Result(column = "surface", property = "surface"),
            @Result(column = "batch_date", property = "batchDate"),
            @Result(column = "created_at", property = "createdAt")
    })
    List<Word> listByDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("SELECT DISTINCT batch_date FROM words WHERE user_id = #{userId} ORDER BY batch_date DESC")
    List<LocalDate> listDates(@Param("userId") Long userId);

    @Delete("DELETE FROM words WHERE user_id = #{userId} AND batch_date = #{date}")
    int deleteByDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Select("SELECT COUNT(*) FROM words WHERE user_id = #{userId}")
    int countAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM words WHERE user_id = #{userId} AND batch_date = #{date}")
    int countByDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}
