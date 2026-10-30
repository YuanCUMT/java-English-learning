package com.dailyreading.mapper;

import com.dailyreading.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE username = #{username}")
    @Results(id = "userMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "username", property = "username"),
            @Result(column = "password_hash", property = "passwordHash"),
            @Result(column = "created_at", property = "createdAt")
    })
    User findByUsername(@Param("username") String username);

    @Select("SELECT id, username, password_hash, created_at FROM users WHERE id = #{id}")
    @ResultMap("userMap")
    User findById(@Param("id") Long id);

    @Insert("INSERT INTO users (username, password_hash) VALUES (#{username}, #{passwordHash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(@Param("username") String username);
}
