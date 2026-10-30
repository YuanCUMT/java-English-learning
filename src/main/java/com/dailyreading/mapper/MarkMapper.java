package com.dailyreading.mapper;

import com.dailyreading.entity.Mark;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MarkMapper {

    String COLS =
            "id, user_id, word, lemma, phonetic, pos, cn, exam_sense, collocation, example, note, status, source, added_at";

    @Select("SELECT " + COLS + " FROM marks WHERE user_id = #{userId} ORDER BY added_at DESC, id DESC")
    List<Mark> listByUser(Long userId);

    @Select("SELECT " + COLS + " FROM marks WHERE user_id = #{userId} AND word = #{word}")
    Mark findByWord(@Param("userId") Long userId, @Param("word") String word);

    @Select("SELECT COUNT(*) FROM marks WHERE user_id = #{userId} AND status = #{status}")
    int countByStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT " + COLS + " FROM marks WHERE id = #{id} AND user_id = #{userId}")
    Mark findById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 同一个词只留一条：已存在就更新释义并重置为「学习中」，避免点两次收藏出两条。
     */
    @Insert("INSERT INTO marks (user_id, word, lemma, phonetic, pos, cn, exam_sense, collocation, example, note, status, source) "
            + "VALUES (#{userId}, #{word}, #{lemma}, #{phonetic}, #{pos}, #{cn}, #{examSense}, #{collocation}, #{example}, #{note}, 'learning', #{source}) "
            + "ON DUPLICATE KEY UPDATE lemma = VALUES(lemma), phonetic = VALUES(phonetic), pos = VALUES(pos), "
            + "cn = VALUES(cn), exam_sense = VALUES(exam_sense), collocation = VALUES(collocation), "
            + "example = VALUES(example), note = VALUES(note), source = VALUES(source)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(Mark mark);

    @Update("UPDATE marks SET status = #{status} WHERE id = #{id} AND user_id = #{userId}")
    int updateStatus(@Param("id") Long id, @Param("userId") Long userId, @Param("status") String status);

    @Delete("DELETE FROM marks WHERE id = #{id} AND user_id = #{userId}")
    int delete(@Param("id") Long id, @Param("userId") Long userId);

    @Delete("DELETE FROM marks WHERE user_id = #{userId} AND status = #{status}")
    int deleteByStatus(@Param("userId") Long userId, @Param("status") String status);
}
