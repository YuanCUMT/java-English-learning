package com.dailyreading.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 首页统计用的几个计数。
 */
@Mapper
public interface StatsMapper {

    @Select("SELECT COUNT(*) FROM words WHERE user_id = #{userId} AND batch_date = CURDATE()")
    int todayWords(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM words WHERE user_id = #{userId}")
    int totalWords(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM marks WHERE user_id = #{userId} AND status = 'learning'")
    int learningMarks(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM articles WHERE user_id = #{userId}")
    int articles(@Param("userId") Long userId);
}
