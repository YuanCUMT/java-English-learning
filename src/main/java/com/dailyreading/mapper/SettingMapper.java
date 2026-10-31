package com.dailyreading.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户自己的配置，现在只存 DeepSeek Key，以后加别的不用改表。 */
@Mapper
public interface SettingMapper {

    @Select("SELECT svalue FROM user_settings WHERE user_id = #{userId} AND skey = #{key}")
    String get(@Param("userId") Long userId, @Param("key") String key);

    @Insert("""
            INSERT INTO user_settings (user_id, skey, svalue)
            VALUES (#{userId}, #{key}, #{value})
            ON DUPLICATE KEY UPDATE svalue = VALUES(svalue)
            """)
    int put(@Param("userId") Long userId, @Param("key") String key, @Param("value") String value);

    @Delete("DELETE FROM user_settings WHERE user_id = #{userId} AND skey = #{key}")
    int remove(@Param("userId") Long userId, @Param("key") String key);
}
