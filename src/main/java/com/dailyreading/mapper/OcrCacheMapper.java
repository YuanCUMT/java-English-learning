package com.dailyreading.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 图片 OCR 结果缓存：同一张图（内容 sha1 相同）只识别一次。
 * 长截图里往往有重复的单词卡，重复导入同一张图时这里能直接省掉一次外部调用。
 */
@Mapper
public interface OcrCacheMapper {

    @Select("SELECT payload FROM ocr_cache WHERE image_sha1 = #{sha1}")
    String findPayload(@Param("sha1") String sha1);

    @Insert("INSERT INTO ocr_cache(image_sha1, payload) VALUES(#{sha1}, #{payload}) "
            + "ON DUPLICATE KEY UPDATE payload = VALUES(payload)")
    int save(@Param("sha1") String sha1, @Param("payload") String payload);
}
