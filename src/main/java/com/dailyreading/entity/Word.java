package com.dailyreading.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Word {
    private Long id;
    private Long userId;
    /** 规范形（小写） */
    private String word;
    /** 原文形态 */
    private String surface;
    private LocalDate batchDate;
    private LocalDateTime createdAt;
}
