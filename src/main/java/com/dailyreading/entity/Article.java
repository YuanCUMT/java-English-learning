package com.dailyreading.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Article {
    private Long id;
    private Long userId;
    private LocalDate articleDate;
    private String mode;
    private String title;
    private String subtitle;
    private String gist;
    private String difficulty;
    private Integer wordCount;
    /** 完整文章 JSON 字符串 */
    private String content;
    private LocalDateTime createdAt;
}
