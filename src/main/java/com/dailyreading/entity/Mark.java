package com.dailyreading.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 生词本条目，对应表 marks。 */
@Data
public class Mark {
    private Long id;
    private Long userId;
    /** 单词或短语，统一小写 */
    private String word;
    private String lemma;
    private String phonetic;
    private String pos;
    private String cn;
    private String examSense;
    private String collocation;
    private String example;
    private String note;
    /** learning / mastered */
    private String status;
    /** ecdict / model */
    private String source;
    private LocalDateTime addedAt;
}
